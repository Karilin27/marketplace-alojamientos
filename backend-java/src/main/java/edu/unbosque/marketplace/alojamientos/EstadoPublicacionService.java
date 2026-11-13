package edu.unbosque.marketplace.alojamientos;

import edu.unbosque.marketplace.alojamientos.AlojamientoDtos.CambioEstadoRespuesta;
import edu.unbosque.marketplace.alojamientos.AlojamientoDtos.EstadoSolicitud;
import edu.unbosque.marketplace.alojamientos.AlojamientoRepositorio.Registro;
import edu.unbosque.marketplace.common.ReglaNegocioException;
import edu.unbosque.marketplace.logs.EventLogger;
import edu.unbosque.marketplace.seguridad.Sesion;
import edu.unbosque.marketplace.seguridad.Sesion.Rol;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * HU-007: el anfitrión activa o pausa su publicación según RN-15.
 * Las reservas CONFIRMADAS no cambian al pausar (CA-03); solo las publicaciones ACTIVAS aparecen en búsquedas (CA-07).
 * Moderar (aprobar, bloquear) es del administrador en HU-018.
 */
@Service
public class EstadoPublicacionService {

    private final AlojamientoRepositorio repo;
    private final AlojamientoConsultas consultas;
    private final TransactionTemplate tx;
    private final EventLogger log;

    public EstadoPublicacionService(AlojamientoRepositorio repo, AlojamientoConsultas consultas, TransactionTemplate tx,
                                    EventLogger log) {
        this.repo = repo;
        this.consultas = consultas;
        this.tx = tx;
        this.log = log;
    }

    public CambioEstadoRespuesta cambiar(Sesion sesion, UUID id, EstadoSolicitud solicitud) {
        sesion.exigirRol(Rol.ANFITRION);                                             // CA-06
        String pedido = solicitud == null || solicitud.estado() == null ? ""
                : solicitud.estado().strip().toUpperCase(Locale.ROOT);
        if (!ReglasAlojamiento.ACTIVA.equals(pedido) && !ReglasAlojamiento.PAUSADA.equals(pedido)) {
            throw ReglaNegocioException.solicitudInvalida("ESTADO_INVALIDO",
                    "El anfitrión solo puede pedir ACTIVA o PAUSADA.", "estado");
        }
        return tx.execute(t -> {
            Registro r = consultas.existente(id, true);
            if (!r.idAnfitrion().equals(sesion.id())) {                               // CA-06
                throw ReglaNegocioException.prohibido("Solo el anfitrión dueño puede cambiar el estado.");
            }
            switch (ReglasAlojamiento.transicionAnfitrion(r.estado(), pedido)) {
                case SIN_CAMBIO -> {
                    return new CambioEstadoRespuesta(id, r.estado(), r.estado(), Instant.now());   // idempotente
                }
                case BLOQUEADA_SOLO_ADMIN -> throw ReglaNegocioException.regla("PUBLICACION_BLOQUEADA",   // CA-05
                        "La publicación fue bloqueada por un administrador; solo él puede cambiar su estado.");
                case EN_REVISION -> throw ReglaNegocioException.regla("TRANSICION_NO_PERMITIDA",          // CA-04
                        "La publicación está en revisión; el administrador debe aprobarla primero.");
                case NO_PERMITIDA -> throw ReglaNegocioException.regla("TRANSICION_NO_PERMITIDA",
                        "No se puede pasar de " + r.estado() + " a " + pedido + ".");
                case PERMITIDA -> { /* sigue */ }
            }
            if (ReglasAlojamiento.ACTIVA.equals(pedido)) {                           // CA-02, RN-16, HU-023 CA-03
                List<String> faltan = ReglasAlojamiento.faltantesParaActivar(consultas.datos(r));
                if (!faltan.isEmpty()) {
                    log.info("PUBLICACION_ACTIVACION_RECHAZADA", sesion.id().toString(), "alojamiento", id.toString(),
                            "RECHAZADO", Map.of("faltantes", String.join(", ", faltan)));
                    throw ReglaNegocioException.regla("INFORMACION_INCOMPLETA",
                            "Complete la información antes de activar la publicación.",
                            faltan.toArray(String[]::new));
                }
            }
            repo.cambiarEstado(id, pedido);
            Instant fecha = repo.registrarCambioEstado(id, r.estado(), pedido, sesion.id(), null);  // CA-08
            log.auditado("PUBLICACION_ESTADO_CAMBIADO", sesion.id().toString(), "alojamiento", id.toString(), "EXITO",
                    Map.of("estadoAnterior", r.estado(), "estadoNuevo", pedido));
            return new CambioEstadoRespuesta(id, r.estado(), pedido, fecha);
        });
    }
}
