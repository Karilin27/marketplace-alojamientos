package edu.unbosque.marketplace.resenas;

import edu.unbosque.marketplace.common.ReglaNegocioException;
import edu.unbosque.marketplace.logs.EventLogger;
import edu.unbosque.marketplace.resenas.ResenaDtos.ResenaAdmin;
import edu.unbosque.marketplace.resenas.ResenaDtos.ResenaPublica;
import edu.unbosque.marketplace.resenas.ResenaDtos.ResenaSolicitud;
import edu.unbosque.marketplace.resenas.ResenaDtos.ResenasAlojamiento;
import edu.unbosque.marketplace.resenas.ResenaDtos.VisibilidadSolicitud;
import edu.unbosque.marketplace.resenas.ResenaRepositorio.ReservaResenable;
import edu.unbosque.marketplace.resenas.ResenaRepositorio.Resena;
import edu.unbosque.marketplace.seguridad.Sesion;
import edu.unbosque.marketplace.seguridad.Sesion.Rol;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** HU-017 (registrar y consultar reseñas) y HU-025 (ocultar o restablecer reseñas). */
@Service
public class ResenaService {

    public static final int LIMITE_ADMIN = 200;

    private final ResenaRepositorio repo;
    private final TransactionTemplate tx;
    private final EventLogger log;

    public ResenaService(ResenaRepositorio repo, TransactionTemplate tx, EventLogger log) {
        this.repo = repo;
        this.tx = tx;
        this.log = log;
    }

    // ------------------------------------------------------------------ HU-017 CA-01 a CA-05, CA-07
    public ResenasAlojamiento registrar(Sesion sesion, ResenaSolicitud s) {
        sesion.exigirRol(Rol.HUESPED);
        if (s == null) {
            throw ReglaNegocioException.solicitudInvalida("RESENA_INVALIDA", "Debe enviar la reseña.");
        }
        List<String> errores = ReglasResena.errores(s.idReserva(), s.calificacion(), s.comentario());
        if (!errores.isEmpty()) {                                                    // CA-05
            throw ReglaNegocioException.solicitudInvalida("RESENA_INVALIDA", "Revise la reseña.",
                    errores.toArray(String[]::new));
        }
        ReservaResenable r = repo.reserva(s.idReserva())
                .orElseThrow(() -> ReglaNegocioException.noEncontrado("La reserva no existe."));
        ReglasResena.Impedimento imp = ReglasResena.impedimento(r.idHuesped(), r.estado(),
                repo.tieneResena(r.id()), sesion.id());
        if (imp != null) {
            log.info("RESENA_RECHAZADA", sesion.id().toString(), "reserva", r.id().toString(), "RECHAZADO",
                    Map.of("motivo", imp.name()));
            throw switch (imp) {
                case RESERVA_AJENA -> ReglaNegocioException.prohibido("Solo puede reseñar sus propias reservas.");
                case NO_COMPLETADA -> ReglaNegocioException.regla("RESERVA_NO_COMPLETADA",            // CA-02
                        "Solo puede reseñar una estancia completada.", "Estado de la reserva: " + r.estado());
                case YA_RESENADA -> ReglaNegocioException.conflicto("RESENA_DUPLICADA",                // CA-03
                        "Esta reserva ya tiene una reseña.");
            };
        }
        UUID id;
        try {
            id = repo.insertar(r.id(), r.idAlojamiento(), sesion.id(), s.calificacion(),           // CA-04
                    ReglasResena.texto(s.comentario()));
        } catch (DuplicateKeyException e) {                                          // dos envíos simultáneos
            throw ReglaNegocioException.conflicto("RESENA_DUPLICADA", "Esta reserva ya tiene una reseña.");
        }
        log.auditado("RESENA_REGISTRADA", sesion.id().toString(), "resena", id.toString(), "EXITO",        // CA-07
                Map.of("idReserva", r.id().toString(), "idAlojamiento", r.idAlojamiento().toString(),
                        "calificacion", s.calificacion()));
        return deAlojamiento(r.idAlojamiento());
    }

    // ------------------------------------------------------------------ HU-017 CA-06 (público)
    public ResenasAlojamiento deAlojamiento(UUID idAlojamiento) {
        if (idAlojamiento == null) {
            throw ReglaNegocioException.solicitudInvalida("PARAMETRO_FALTANTE", "Indique accommodationId.",
                    "accommodationId");
        }
        if (!repo.existeAlojamiento(idAlojamiento)) {
            throw ReglaNegocioException.noEncontrado("El alojamiento no existe.");
        }
        List<ResenaPublica> lista = repo.visibles(idAlojamiento);
        return new ResenasAlojamiento(idAlojamiento, repo.promedio(idAlojamiento), lista.size(), lista);
    }

    // ------------------------------------------------------------------ HU-025
    public List<ResenaAdmin> paraAdministrador(Sesion sesion, UUID idAlojamiento) {
        sesion.exigirRol(Rol.ADMINISTRADOR);                                         // CA-04
        return repo.paraAdministrador(idAlojamiento, LIMITE_ADMIN);
    }

    public ResenasAlojamiento cambiarVisibilidad(Sesion sesion, UUID id, VisibilidadSolicitud s) {
        sesion.exigirRol(Rol.ADMINISTRADOR);                                         // CA-04
        Boolean visible = s == null ? null : s.visible();
        String motivo = s == null ? null : ReglasResena.texto(s.motivo());
        List<String> errores = ReglasResena.erroresVisibilidad(visible, motivo);
        if (!errores.isEmpty()) {
            throw ReglaNegocioException.solicitudInvalida("VISIBILIDAD_INVALIDA", "Revise la solicitud.",
                    errores.toArray(String[]::new));
        }
        UUID idAlojamiento = tx.execute(t -> {
            Resena r = repo.buscarBloqueando(id)
                    .orElseThrow(() -> ReglaNegocioException.noEncontrado("La reseña no existe."));
            if (r.visible() != visible) {                                            // idempotente si no cambia
                repo.cambiarVisibilidad(id, visible, motivo);                        // CA-01, CA-02, CA-03
                repo.registrarModeracion(sesion.id(), id, r.idAlojamiento(),         // CA-05
                        visible ? "RESTABLECER_RESENA" : "OCULTAR_RESENA", motivo);
            }
            return r.idAlojamiento();
        });
        log.auditado(visible ? "RESENA_RESTABLECIDA" : "RESENA_OCULTADA", sesion.id().toString(), "resena",
                id.toString(), "EXITO", motivo == null ? Map.of() : Map.of("motivo", motivo));
        return deAlojamiento(idAlojamiento);
    }
}
