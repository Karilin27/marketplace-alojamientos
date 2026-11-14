package edu.unbosque.marketplace.administracion;

import edu.unbosque.marketplace.administracion.AdminDtos.Dashboard;
import edu.unbosque.marketplace.administracion.AdminDtos.DecisionRevisionSolicitud;
import edu.unbosque.marketplace.administracion.AdminDtos.EstadoUsuarioSolicitud;
import edu.unbosque.marketplace.administracion.AdminDtos.ModeracionRespuesta;
import edu.unbosque.marketplace.administracion.AdminDtos.ModeracionSolicitud;
import edu.unbosque.marketplace.administracion.AdminDtos.PaginaAuditoria;
import edu.unbosque.marketplace.administracion.AdminDtos.PublicacionAdmin;
import edu.unbosque.marketplace.administracion.AdminDtos.ReservaRevision;
import edu.unbosque.marketplace.administracion.AdminDtos.UsuarioAdmin;
import edu.unbosque.marketplace.administracion.AdminRepositorio.Cuenta;
import edu.unbosque.marketplace.administracion.AdminRepositorio.Publicacion;
import edu.unbosque.marketplace.administracion.ReglasAdministracion.Accion;
import edu.unbosque.marketplace.alojamientos.AlojamientoConsultas;
import edu.unbosque.marketplace.alojamientos.ReglasAlojamiento;
import edu.unbosque.marketplace.common.ReglaNegocioException;
import edu.unbosque.marketplace.logs.EventLogger;
import edu.unbosque.marketplace.notificaciones.NotificacionService;
import edu.unbosque.marketplace.seguridad.Sesion;
import edu.unbosque.marketplace.seguridad.Sesion.Rol;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** S6 — HU-018 (moderar publicaciones), HU-019 (auditoría), HU-020 (dashboard) y HU-022 (administrar usuarios). */
@Service
public class AdminService {

    public static final int TAMANO_MAXIMO = 100;

    private final AdminRepositorio repo;
    private final AlojamientoConsultas alojamientos;
    private final NotificacionService notificaciones;
    private final TransactionTemplate tx;
    private final EventLogger log;
    private final Clock reloj;

    public AdminService(AdminRepositorio repo, AlojamientoConsultas alojamientos, NotificacionService notificaciones,
                        TransactionTemplate tx, EventLogger log, Clock reloj) {
        this.repo = repo;
        this.alojamientos = alojamientos;
        this.notificaciones = notificaciones;
        this.tx = tx;
        this.log = log;
        this.reloj = reloj;
    }

    // ================================================================== HU-022
    public List<UsuarioAdmin> usuarios(Sesion sesion, String buscar, String rol, String estado) {
        sesion.exigirRol(Rol.ADMINISTRADOR);                                         // CA-04
        return repo.usuarios(ReglasAdministracion.texto(buscar), ReglasAdministracion.mayusculas(rol),
                ReglasAdministracion.mayusculas(estado));                            // CA-01
    }

    public UsuarioAdmin cambiarEstadoUsuario(Sesion sesion, UUID id, EstadoUsuarioSolicitud s) {
        sesion.exigirRol(Rol.ADMINISTRADOR);
        String estado = s == null ? null : ReglasAdministracion.mayusculas(s.estado());
        String motivo = s == null ? null : ReglasAdministracion.texto(s.motivo());
        String[] previo = new String[1];
        tx.executeWithoutResult(t -> {
            Cuenta c = repo.cuentaBloqueando(id).orElseThrow(() -> ReglaNegocioException.noEncontrado(
                    "El usuario no existe."));
            List<String> errores = ReglasAdministracion.erroresCambioEstado(estado, motivo,
                    c.id().equals(sesion.id()), "ADMINISTRADOR".equals(c.rol()));
            if (!errores.isEmpty()) {
                throw ReglaNegocioException.solicitudInvalida("ESTADO_CUENTA_INVALIDO", "Revise la solicitud.",
                        errores.toArray(String[]::new));
            }
            previo[0] = c.estado();
            repo.cambiarEstadoCuenta(id, estado, motivo);                             // CA-02, CA-03
            notificaciones.notificar(id, "RESTRINGIDO".equals(estado) ? "CUENTA_RESTRINGIDA" : "CUENTA_REACTIVADA",
                    "RESTRINGIDO".equals(estado) ? "Su cuenta fue restringida: " + motivo
                            : "Su cuenta fue reactivada por un administrador.", null);
        });
        log.auditado("RESTRINGIDO".equals(estado) ? "USUARIO_RESTRINGIDO" : "USUARIO_REACTIVADO",           // CA-05
                sesion.id().toString(), "usuario", id.toString(), "EXITO",
                motivo == null ? Map.of("estadoAnterior", previo[0]) : Map.of("estadoAnterior", previo[0], "motivo", motivo));
        return repo.usuarios(null, null, null).stream().filter(u -> u.id().equals(id)).findFirst().orElse(null);
    }

    // ================================================================== HU-018
    public List<PublicacionAdmin> publicaciones(Sesion sesion, String estado) {
        sesion.exigirRol(Rol.ADMINISTRADOR);                                         // CA-02
        return repo.publicaciones(ReglasAdministracion.mayusculas(estado));          // CA-01
    }

    public ModeracionRespuesta moderar(Sesion sesion, UUID id, ModeracionSolicitud s) {
        sesion.exigirRol(Rol.ADMINISTRADOR);
        Accion accion = ReglasAdministracion.accion(s == null ? null : s.accion());
        String motivo = s == null ? null : ReglasAdministracion.texto(s.motivo());
        if (accion == null) {
            throw ReglaNegocioException.solicitudInvalida("ACCION_INVALIDA",
                    "La acción debe ser APROBAR, PAUSAR, BLOQUEAR o DESBLOQUEAR.", "accion");
        }
        if (ReglasAdministracion.exigeMotivo(accion) && motivo == null) {             // CA-05 / CU-17 E1
            throw ReglaNegocioException.solicitudInvalida("MOTIVO_REQUERIDO", "Indique el motivo del bloqueo.",
                    "motivo");
        }
        ModeracionRespuesta r = tx.execute(t -> {
            Publicacion p = repo.publicacionBloqueando(id).orElseThrow(() -> ReglaNegocioException.noEncontrado(
                    "La publicación no existe."));
            String nuevo = ReglasAdministracion.destino(accion, p.estado());
            if (nuevo == null) {
                throw ReglaNegocioException.regla("TRANSICION_NO_PERMITIDA",
                        "No se puede " + accion.name().toLowerCase() + " una publicación en estado " + p.estado() + ".");
            }
            if ("ACTIVA".equals(nuevo)) {                                            // RN-16 también al aprobar
                List<String> faltan = ReglasAlojamiento.faltantesParaActivar(
                        alojamientos.datos(alojamientos.existente(id, false)));
                if (!faltan.isEmpty()) {
                    throw ReglaNegocioException.regla("INFORMACION_INCOMPLETA",
                            "La publicación no tiene la información mínima para activarse.",
                            faltan.toArray(String[]::new));
                }
            }
            repo.cambiarEstadoPublicacion(id, nuevo);                                // CA-03, CA-04
            int enRevision = accion == Accion.BLOQUEAR ? repo.marcarRevisionManual(id) : 0;   // CA-05, RN-19
            Instant fecha = repo.registrarModeracion(sesion.id(), id, accion.name(), motivo);  // CA-07
            repo.registrarHistorialPublicacion(id, p.estado(), nuevo, sesion.id(), motivo);
            notificaciones.notificar(p.idAnfitrion(), "PUBLICACION_MODERADA",                // CA-06
                    "Su publicación «" + p.titulo() + "» pasó a " + nuevo
                            + (motivo == null ? "." : ". Motivo: " + motivo), null);
            return new ModeracionRespuesta(id, p.estado(), nuevo, enRevision, fecha);
        });
        log.auditado("PUBLICACION_" + accion.name(), sesion.id().toString(), "alojamiento", id.toString(), "EXITO",
                motivo == null ? Map.of("estadoNuevo", r.estadoNuevo())
                        : Map.of("estadoNuevo", r.estadoNuevo(), "motivo", motivo));
        return r;
    }

    /** CU-17 A3: reservas marcadas para revisión manual y registro de la decisión tomada. */
    public List<ReservaRevision> reservasEnRevision(Sesion sesion) {
        sesion.exigirRol(Rol.ADMINISTRADOR);
        return repo.reservasEnRevision();
    }

    public Map<String, String> cerrarRevision(Sesion sesion, UUID idReserva, DecisionRevisionSolicitud s) {
        sesion.exigirRol(Rol.ADMINISTRADOR);
        String decision = s == null ? null : ReglasAdministracion.texto(s.decision());
        if (decision == null) {
            throw ReglaNegocioException.solicitudInvalida("DECISION_REQUERIDA", "Registre la decisión tomada.",
                    "decision");
        }
        if (repo.cerrarRevision(idReserva) == 0) {
            throw ReglaNegocioException.noEncontrado("La reserva no está en revisión manual.");
        }
        log.auditado("REVISION_MANUAL_CERRADA", sesion.id().toString(), "reserva", idReserva.toString(), "EXITO",
                Map.of("decision", decision));
        return Map.of("mensaje", "Decisión registrada.");
    }

    // ================================================================== HU-019
    public PaginaAuditoria auditoria(Sesion sesion, UUID usuario, String entidad, String accion, LocalDate desde,
                                     LocalDate hasta, int page, int size) {
        sesion.exigirRol(Rol.ADMINISTRADOR);                                         // CA-04
        if (page < 0 || size < 1 || size > TAMANO_MAXIMO || (desde != null && hasta != null && hasta.isBefore(desde))) {
            throw ReglaNegocioException.solicitudInvalida("FILTROS_INVALIDOS", "Revise los filtros.",
                    "page >= 0, size entre 1 y " + TAMANO_MAXIMO + ", hasta no anterior a desde");
        }
        long[] total = new long[1];
        var items = repo.auditoria(usuario, ReglasAdministracion.texto(entidad), ReglasAdministracion.texto(accion),
                desde, hasta, page, size, total);                                    // CA-01, CA-02, CA-03
        return new PaginaAuditoria(items, page, size, total[0]);
    }

    // ================================================================== HU-020
    public Dashboard dashboard(Sesion sesion) {
        sesion.exigirRol(Rol.ADMINISTRADOR);                                         // CA-05
        Map<String, Long> porRol = repo.conteo("SELECT rol, count(*) FROM identidad.usuario GROUP BY rol ORDER BY rol");
        Map<String, Long> porEstado = new HashMap<>();
        for (String e : List.of("PENDIENTE_PAGO", "CONFIRMADA", "RECHAZADA", "CANCELADA", "COMPLETADA")) {
            porEstado.put(e, 0L);                                                    // CA-04: ceros sin datos
        }
        porEstado.putAll(repo.conteo("SELECT estado, count(*) FROM reservas.reserva GROUP BY estado"));
        return new Dashboard(porRol.values().stream().mapToLong(Long::longValue).sum(), porRol,          // CA-01
                repo.numero("SELECT count(*) FROM catalogo.alojamiento WHERE estado_publicacion = 'ACTIVA'"),
                porEstado, repo.ingresosEstimados(), repo.ciudadesMayorDemanda(5), reloj.instant());  // CA-02, CA-03
    }
}
