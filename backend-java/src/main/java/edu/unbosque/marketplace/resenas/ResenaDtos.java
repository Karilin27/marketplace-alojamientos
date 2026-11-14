package edu.unbosque.marketplace.resenas;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** S5 — Cuerpos JSON de reseñas (página 13: /api/reviews y /api/admin/reviews). */
public final class ResenaDtos {

    private ResenaDtos() {
    }

    /** HU-017: el huésped califica una reserva COMPLETADA propia. */
    public record ResenaSolicitud(UUID idReserva, Integer calificacion, String comentario) {
    }

    /** Reseña visible para el público (el autor se muestra solo con su nombre). */
    public record ResenaPublica(UUID id, int calificacion, String comentario, String autor, Instant fecha) {
    }

    /** HU-017 CA-06: promedio y reseñas visibles de un alojamiento. */
    public record ResenasAlojamiento(UUID idAlojamiento, BigDecimal calificacionPromedio, int total,
                                     List<ResenaPublica> resenas) {
    }

    /** Vista del administrador (HU-025): incluye las ocultas y su motivo. */
    public record ResenaAdmin(UUID id, UUID idAlojamiento, String alojamiento, UUID idReserva, UUID idUsuario,
                              String autor, int calificacion, String comentario, boolean visible, String motivoOculta,
                              Instant fecha) {
    }

    /** HU-025: ocultar (visible=false, con motivo) o restablecer (visible=true). */
    public record VisibilidadSolicitud(Boolean visible, String motivo) {
    }
}
