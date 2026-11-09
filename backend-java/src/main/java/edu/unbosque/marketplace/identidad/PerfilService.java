package edu.unbosque.marketplace.identidad;

import edu.unbosque.marketplace.common.ReglaNegocioException;
import edu.unbosque.marketplace.identidad.PerfilDtos.EstadoRespuesta;
import edu.unbosque.marketplace.identidad.PerfilDtos.EstadoSolicitud;
import edu.unbosque.marketplace.identidad.PerfilDtos.PerfilRespuesta;
import edu.unbosque.marketplace.identidad.PerfilDtos.PerfilSolicitud;
import edu.unbosque.marketplace.identidad.PerfilRepositorio.Cuenta;
import edu.unbosque.marketplace.logs.EventLogger;
import edu.unbosque.marketplace.seguridad.Sesion;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * HU-004: consultar y actualizar el perfil propio, y desactivar o reactivar la cuenta.
 * CA-04 (solo su propia cuenta) se cumple por construcción: todo se resuelve con el id de la sesión (JWT),
 * nunca con un id recibido en la ruta o el cuerpo.
 */
@Service
public class PerfilService {

    static final String ACTIVO = "ACTIVO";
    static final String DESACTIVADO = "DESACTIVADO";
    static final String RESTRINGIDO = "RESTRINGIDO";

    private final PerfilRepositorio repo;
    private final TransactionTemplate tx;
    private final Clock reloj;
    private final EventLogger log;

    public PerfilService(PerfilRepositorio repo, TransactionTemplate tx, Clock reloj, EventLogger log) {
        this.repo = repo;
        this.tx = tx;
        this.reloj = reloj;
        this.log = log;
    }

    // ------------------------------------------------------------------ CA-01
    public PerfilRespuesta obtener(Sesion sesion) {
        return respuesta(repo.buscar(sesion.id()).orElseThrow(PerfilService::noExiste));
    }

    // ------------------------------------------------------------------ CA-02, CA-03
    public PerfilRespuesta actualizar(Sesion sesion, PerfilSolicitud solicitud) {
        if (solicitud == null) {
            throw ReglaNegocioException.solicitudInvalida("PERFIL_INVALIDO", "Debe enviar los datos del perfil.");
        }
        var datos = ReglasPerfil.normalizar(solicitud);
        List<String> errores = ReglasPerfil.errores(datos);
        if (!errores.isEmpty()) {
            throw ReglaNegocioException.solicitudInvalida("PERFIL_INVALIDO",
                    "Hay datos del perfil que debe corregir.", errores.toArray(String[]::new));
        }
        return tx.execute(estado -> {
            repo.buscarBloqueando(sesion.id()).orElseThrow(PerfilService::noExiste);
            repo.actualizar(sesion.id(), datos);
            log.info("PERFIL_ACTUALIZADO", sesion.id().toString(), "usuario", sesion.id().toString(), "EXITO",
                    Map.of());
            return respuesta(repo.buscar(sesion.id()).orElseThrow(PerfilService::noExiste));
        });
    }

    // ------------------------------------------------------------------ CA-05, CA-06, CA-07
    public EstadoRespuesta cambiarEstado(Sesion sesion, EstadoSolicitud solicitud) {
        String pedido = solicitud == null || solicitud.estado() == null ? "" : solicitud.estado().strip().toUpperCase();
        if (!ACTIVO.equals(pedido) && !DESACTIVADO.equals(pedido)) {
            throw ReglaNegocioException.solicitudInvalida("ESTADO_INVALIDO",
                    "El estado debe ser ACTIVO o DESACTIVADO.", "estado");
        }
        return tx.execute(t -> {
            Cuenta cuenta = repo.buscarBloqueando(sesion.id()).orElseThrow(PerfilService::noExiste);
            if (RESTRINGIDO.equals(cuenta.estado())) {
                throw ReglaNegocioException.prohibido(
                        "Su cuenta fue restringida por un administrador; no puede cambiar su estado.");
            }
            if (pedido.equals(cuenta.estado())) {
                return new EstadoRespuesta(pedido, DESACTIVADO.equals(pedido), 0);   // idempotente
            }
            if (DESACTIVADO.equals(pedido)) {
                return desactivar(sesion, cuenta);
            }
            repo.cambiarEstado(cuenta.id(), ACTIVO);                                // CA-07: publicaciones siguen PAUSADAS
            log.auditado("CUENTA_REACTIVADA", sesion.id().toString(), "usuario", sesion.id().toString(), "EXITO",
                    Map.of());
            return new EstadoRespuesta(ACTIVO, false, 0);
        });
    }

    private EstadoRespuesta desactivar(Sesion sesion, Cuenta cuenta) {
        long pendientes = repo.reservasConfirmadasFuturas(cuenta.id(), LocalDate.now(reloj));
        if (pendientes > 0) {                                                       // CA-06
            log.auditado("CUENTA_DESACTIVACION_RECHAZADA", sesion.id().toString(), "usuario",
                    sesion.id().toString(), "RECHAZADO", Map.of("reservasConfirmadas", pendientes));
            throw ReglaNegocioException.regla("RESERVAS_CONFIRMADAS_PENDIENTES",
                    "No puede desactivar su cuenta mientras tenga reservas confirmadas futuras.",
                    "Reservas confirmadas futuras: " + pendientes);
        }
        repo.cambiarEstado(cuenta.id(), DESACTIVADO);                               // CA-05
        int pausadas = repo.pausarPublicacionesActivas(cuenta.id());
        log.auditado("CUENTA_DESACTIVADA", sesion.id().toString(), "usuario", sesion.id().toString(), "EXITO",
                Map.of("publicacionesPausadas", pausadas));
        return new EstadoRespuesta(DESACTIVADO, true, pausadas);
    }

    private static PerfilRespuesta respuesta(Cuenta c) {
        return new PerfilRespuesta(c.id(), c.nombre(), c.correo(), c.telefono(), c.documento(), c.pais(),
                c.fotoUrl(), c.rol(), c.estado());
    }

    private static ReglaNegocioException noExiste() {
        return ReglaNegocioException.noEncontrado("La cuenta no existe.");
    }
}
