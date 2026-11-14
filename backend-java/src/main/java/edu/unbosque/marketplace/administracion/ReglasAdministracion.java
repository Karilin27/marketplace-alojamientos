package edu.unbosque.marketplace.administracion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reglas puras de S6 y de la recuperación de acceso de S1 (sin base de datos), probadas con JUnit.
 * RN-14 (acciones administrativas auditadas), RN-15 (estados de publicación), RN-19 (revisión manual),
 * RN-21 (reportes PDF o Excel) y RN-24 (restricción de cuentas con motivo).
 */
public final class ReglasAdministracion {

    public static final int MIN_CONTRASENA = 8;
    public static final int MINUTOS_TOKEN = 30;
    public static final Set<String> TIPOS_REPORTE = Set.of("USUARIOS", "PUBLICACIONES", "RESERVAS", "INGRESOS", "DEMANDA");
    public static final Set<String> FORMATOS = Set.of("PDF", "EXCEL");

    private ReglasAdministracion() {
    }

    // ------------------------------------------------------------------ HU-018: moderación de publicaciones

    public enum Accion { APROBAR, PAUSAR, BLOQUEAR, DESBLOQUEAR }

    /** Estado al que lleva cada acción del administrador, o null si no se permite desde el estado actual. */
    public static String destino(Accion accion, String estadoActual) {
        return switch (accion) {
            case APROBAR -> "PENDIENTE_REVISION".equals(estadoActual) || "BORRADOR".equals(estadoActual)
                    || "PAUSADA".equals(estadoActual) ? "ACTIVA" : null;
            case PAUSAR -> "ACTIVA".equals(estadoActual) || "PENDIENTE_REVISION".equals(estadoActual) ? "PAUSADA" : null;
            case BLOQUEAR -> "BLOQUEADA".equals(estadoActual) ? null : "BLOQUEADA";
            case DESBLOQUEAR -> "BLOQUEADA".equals(estadoActual) ? "PAUSADA" : null;
        };
    }

    /** CU-17 E1: bloquear exige motivo. */
    public static boolean exigeMotivo(Accion accion) {
        return accion == Accion.BLOQUEAR;
    }

    public static Accion accion(String texto) {
        if (texto == null) return null;
        try {
            return Accion.valueOf(texto.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ HU-022: administrar usuarios

    /** Estado al que pasa una cuenta: RESTRINGIDO (con motivo) o ACTIVO (reactivar). Devuelve los errores. */
    public static List<String> erroresCambioEstado(String nuevoEstado, String motivo, boolean esSuPropiaCuenta,
                                                   boolean esAdministrador) {
        List<String> e = new ArrayList<>();
        if (!"RESTRINGIDO".equals(nuevoEstado) && !"ACTIVO".equals(nuevoEstado)) {
            e.add("estado: debe ser RESTRINGIDO o ACTIVO");
        }
        if ("RESTRINGIDO".equals(nuevoEstado) && texto(motivo) == null) {
            e.add("motivo: es obligatorio para restringir una cuenta");
        }
        if (esSuPropiaCuenta) e.add("No puede cambiar el estado de su propia cuenta");
        if (esAdministrador && "RESTRINGIDO".equals(nuevoEstado)) e.add("No se puede restringir a otro administrador");
        return e;
    }

    // ------------------------------------------------------------------ HU-003: recuperación de acceso

    public static List<String> erroresNuevaContrasena(String token, String contrasena) {
        List<String> e = new ArrayList<>();
        if (texto(token) == null) e.add("token: es obligatorio");
        if (contrasena == null || contrasena.length() < MIN_CONTRASENA) {
            e.add("nuevaContrasena: debe tener al menos " + MIN_CONTRASENA + " caracteres");
        }
        return e;
    }

    // ------------------------------------------------------------------ HU-021: reportes

    public static List<String> erroresReporte(String tipo, String formato) {
        List<String> e = new ArrayList<>();
        if (tipo == null || !TIPOS_REPORTE.contains(tipo)) {
            e.add("tipo: debe ser uno de " + String.join(", ", TIPOS_REPORTE.stream().sorted().toList()));
        }
        if (formato == null || !FORMATOS.contains(formato)) e.add("formato: debe ser PDF o EXCEL");
        return e;
    }

    public static String mayusculas(String s) {
        String t = texto(s);
        return t == null ? null : t.toUpperCase(Locale.ROOT);
    }

    public static String texto(String s) {
        if (s == null) return null;
        String t = s.strip();
        return t.isEmpty() ? null : t;
    }
}
