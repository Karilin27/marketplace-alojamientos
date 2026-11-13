package edu.unbosque.marketplace.alojamientos;

import edu.unbosque.marketplace.alojamientos.AlojamientoDtos.AlojamientoRespuesta;
import edu.unbosque.marketplace.alojamientos.AlojamientoDtos.AlojamientoSolicitud;
import edu.unbosque.marketplace.alojamientos.AlojamientoRepositorio.Registro;
import edu.unbosque.marketplace.alojamientos.ReglasAlojamiento.Datos;
import edu.unbosque.marketplace.common.ReglaNegocioException;
import edu.unbosque.marketplace.logs.EventLogger;
import edu.unbosque.marketplace.notificaciones.NotificacionService;
import edu.unbosque.marketplace.seguridad.Sesion;
import edu.unbosque.marketplace.seguridad.Sesion.Rol;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * HU-005 (crear publicación), HU-006 (editar publicación) y HU-007 CA-09 (consultar mis publicaciones).
 * La política de cancelación se asocia aquí con idPoliticaCancelacion (HU-023 CA-02).
 */
@Service
public class PublicacionService {

    public static final String NOTIF_PUBLICACION_EN_REVISION = "PUBLICACION_EN_REVISION";

    private final AlojamientoRepositorio repo;
    private final AlojamientoConsultas consultas;
    private final TransactionTemplate tx;
    private final EventLogger log;
    private final NotificacionService notificaciones;

    public PublicacionService(AlojamientoRepositorio repo, AlojamientoConsultas consultas, TransactionTemplate tx,
                              EventLogger log, NotificacionService notificaciones) {
        this.repo = repo;
        this.consultas = consultas;
        this.tx = tx;
        this.log = log;
        this.notificaciones = notificaciones;
    }

    // ------------------------------------------------------------------ HU-005
    public AlojamientoRespuesta crear(Sesion sesion, AlojamientoSolicitud solicitud) {
        sesion.exigirRol(Rol.ANFITRION);                                             // CA-05
        Datos d = validar(solicitud);
        UUID id = tx.execute(t -> {
            UUID nuevo = repo.insertar(sesion.id(), d);                              // CA-03: BORRADOR del anfitrión
            repo.reemplazarServicios(nuevo, d.servicios());
            repo.reemplazarFotos(nuevo, d.imagenes());
            return nuevo;
        });
        log.auditado("PUBLICACION_CREADA", sesion.id().toString(), "alojamiento", id.toString(), "EXITO",
                Map.of("estado", ReglasAlojamiento.BORRADOR));                          // CA-06
        return consultas.respuesta(id, false);
    }

    // ------------------------------------------------------------------ HU-006
    public AlojamientoRespuesta editar(Sesion sesion, UUID id, AlojamientoSolicitud solicitud) {
        sesion.exigirRol(Rol.ANFITRION, Rol.ADMINISTRADOR);                          // CA-04
        Datos d = validar(solicitud);
        String[] resultado = tx.execute(t -> {
            Registro r = consultas.existente(id, true);
            consultas.exigirDuenoOAdmin(sesion, r);                                  // CA-03, CA-05
            Datos antes = consultas.datos(r);
            repo.actualizar(id, d);                                                  // CA-07: las reservas guardan
            repo.reemplazarServicios(id, d.servicios());                            // su propio total y política
            repo.reemplazarFotos(id, d.imagenes());
            String nuevoEstado = r.estado();
            if (ReglasAlojamiento.ACTIVA.equals(r.estado()) && !sesion.esAdmin()
                    && ReglasAlojamiento.cambiaCamposCriticos(antes, d)) {           // CA-06
                nuevoEstado = ReglasAlojamiento.PENDIENTE_REVISION;
                repo.cambiarEstado(id, nuevoEstado);
                repo.registrarCambioEstado(id, r.estado(), nuevoEstado, sesion.id(),
                        "Edición de título, descripción, ubicación o tipo");
                for (UUID admin : repo.administradoresActivos()) {
                    notificaciones.notificar(admin, NOTIF_PUBLICACION_EN_REVISION,
                            "La publicación «" + d.titulo() + "» fue editada y espera revisión.", null);
                }
            }
            return new String[] {r.estado(), nuevoEstado};
        });
        log.auditado(sesion.esAdmin() ? "PUBLICACION_EDITADA_POR_ADMIN" : "PUBLICACION_EDITADA",
                sesion.id().toString(), "alojamiento", id.toString(), "EXITO",
                Map.of("estadoAnterior", resultado[0], "estadoNuevo", resultado[1]));
        return consultas.respuesta(id, false);
    }

    // ------------------------------------------------------------------ HU-007 CA-09
    public List<AlojamientoRespuesta> misPublicaciones(Sesion sesion) {
        sesion.exigirRol(Rol.ANFITRION);
        return repo.delAnfitrion(sesion.id()).stream().map(r -> consultas.respuesta(r, false)).toList();
    }

    /** Detalle para el dueño o el administrador, en cualquier estado (el público solo ve ACTIVAS). */
    public AlojamientoRespuesta verPropia(Sesion sesion, UUID id) {
        Registro r = consultas.existente(id, false);
        consultas.exigirDuenoOAdmin(sesion, r);
        return consultas.respuesta(r, false);
    }

    private Datos validar(AlojamientoSolicitud solicitud) {
        if (solicitud == null) {
            throw ReglaNegocioException.solicitudInvalida("ALOJAMIENTO_INVALIDO", "Debe enviar los datos de la publicación.");
        }
        Datos d = ReglasAlojamiento.normalizar(solicitud);
        List<String> errores = new ArrayList<>(ReglasAlojamiento.errores(d));
        List<String> disponibles = repo.serviciosDisponibles();
        for (String s : d.servicios()) {
            if (disponibles.stream().noneMatch(x -> x.equalsIgnoreCase(s))) {
                errores.add("servicios: «" + s + "» no existe. Disponibles: " + String.join(", ", disponibles));
            }
        }
        if (d.idPolitica() != null && repo.politica(d.idPolitica()).isEmpty()) {
            errores.add("idPoliticaCancelacion: la política no existe");
        }
        if (!errores.isEmpty()) {
            throw ReglaNegocioException.solicitudInvalida("ALOJAMIENTO_INVALIDO",
                    "Hay datos de la publicación que debe corregir.", errores.toArray(String[]::new));
        }
        return new Datos(d.titulo(), d.descripcion(), d.tipo(), d.ciudad(), d.direccion(), d.capacidad(), d.precio(),
                d.servicios().stream().map(s -> s.toLowerCase(Locale.ROOT)).distinct().toList(), d.reglas(),
                d.imagenes(), d.idPolitica());
    }
}
