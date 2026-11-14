package edu.unbosque.marketplace.resenas;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Reglas puras de S5 (sin base de datos): RN-13 (una reseña por reserva COMPLETADA) y RN-20 (ocultar con motivo). */
public final class ReglasResena {

    public static final int MAX_COMENTARIO = 1000;
    public static final int MAX_MOTIVO = 500;

    private ReglasResena() {
    }

    /** HU-017 CA-05: calificación de 1 a 5 y comentario no vacío. */
    public static List<String> errores(UUID idReserva, Integer calificacion, String comentario) {
        List<String> e = new ArrayList<>();
        if (idReserva == null) e.add("idReserva: es obligatorio");
        if (calificacion == null || calificacion < 1 || calificacion > 5) e.add("calificacion: debe estar entre 1 y 5");
        String c = texto(comentario);
        if (c == null) {
            e.add("comentario: es obligatorio");
        } else if (c.length() > MAX_COMENTARIO) {
            e.add("comentario: máximo " + MAX_COMENTARIO + " caracteres");
        }
        return e;
    }

    /** Por qué una reserva no se puede reseñar; null si sí se puede (CA-01, CA-02, CA-03). */
    public enum Impedimento { RESERVA_AJENA, NO_COMPLETADA, YA_RESENADA }

    public static Impedimento impedimento(UUID huespedReserva, String estadoReserva, boolean yaTieneResena,
                                          UUID solicitante) {
        if (!solicitante.equals(huespedReserva)) return Impedimento.RESERVA_AJENA;
        if (!"COMPLETADA".equals(estadoReserva)) return Impedimento.NO_COMPLETADA;
        if (yaTieneResena) return Impedimento.YA_RESENADA;
        return null;
    }

    /** HU-025 CA-01: para ocultar se exige un motivo. */
    public static List<String> erroresVisibilidad(Boolean visible, String motivo) {
        List<String> e = new ArrayList<>();
        if (visible == null) e.add("visible: indique true (restablecer) o false (ocultar)");
        String m = texto(motivo);
        if (Boolean.FALSE.equals(visible) && m == null) e.add("motivo: es obligatorio para ocultar una reseña");
        if (m != null && m.length() > MAX_MOTIVO) e.add("motivo: máximo " + MAX_MOTIVO + " caracteres");
        return e;
    }

    static String texto(String s) {
        if (s == null) return null;
        String t = s.strip();
        return t.isEmpty() ? null : t;
    }
}
