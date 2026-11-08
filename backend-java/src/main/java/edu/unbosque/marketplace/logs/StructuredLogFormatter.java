package edu.unbosque.marketplace.logs;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Formatea eventos como una línea JSON (HU-026, RNF-018, RN-22). Campos: timestamp, nivel,
 * evento, usuario, entidad, idEntidad, resultado y detalle. Nunca escribe contraseñas, tokens
 * ni datos de tarjeta (HU-026 CA-04): oculta por nombre de clave y por patrón de valor.
 * No depende de librerías externas.
 */
public final class StructuredLogFormatter {

    private static final Pattern CLAVE_SENSIBLE = Pattern.compile(
            "(?i).*(password|contrase|clave|secret|token|authorization|tarjeta|card|cvv|cvc|payment_?method|metodo_?pago).*");
    private static final Pattern VALOR_TARJETA = Pattern.compile("(?<![\\w-])(?:\\d[ -]?){12,18}\\d(?![\\w-])");
    private static final Pattern VALOR_JWT = Pattern.compile("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]*");
    private static final Pattern VALOR_BEARER = Pattern.compile("(?i)bearer\\s+\\S+");
    static final String OCULTO = "***";

    private StructuredLogFormatter() {
    }

    public static String formatear(Instant fecha, NivelLog nivel, String evento, String usuario,
                                   String entidad, String idEntidad, String resultado,
                                   Map<String, ?> detalle) {
        StringBuilder sb = new StringBuilder(256).append('{');
        campo(sb, "timestamp", fecha.toString()).append(',');
        campo(sb, "nivel", nivel.name()).append(',');
        campo(sb, "evento", evento).append(',');
        campo(sb, "usuario", usuario).append(',');
        campo(sb, "entidad", entidad).append(',');
        campo(sb, "idEntidad", idEntidad).append(',');
        campo(sb, "resultado", resultado).append(',');
        sb.append("\"detalle\":");
        valor(sb, detalle == null ? Map.of() : detalle, null);
        return sb.append('}').toString();
    }

    private static StringBuilder campo(StringBuilder sb, String nombre, String v) {
        sb.append('"').append(nombre).append("\":");
        if (v == null) {
            return sb.append("null");
        }
        return sb.append('"').append(escapar(limpiar(v))).append('"');
    }

    @SuppressWarnings("unchecked")
    private static void valor(StringBuilder sb, Object v, String clave) {
        if (v == null) {
            sb.append("null");
        } else if (clave != null && CLAVE_SENSIBLE.matcher(clave).matches()) {
            sb.append('"').append(OCULTO).append('"');
        } else if (v instanceof Map<?, ?> m) {
            sb.append('{');
            boolean primero = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!primero) {
                    sb.append(',');
                }
                primero = false;
                String k = String.valueOf(e.getKey());
                sb.append('"').append(escapar(k)).append("\":");
                valor(sb, e.getValue(), k);
            }
            sb.append('}');
        } else if (v instanceof Collection<?> c) {
            sb.append('[');
            boolean primero = true;
            for (Object o : c) {
                if (!primero) {
                    sb.append(',');
                }
                primero = false;
                valor(sb, o, clave);
            }
            sb.append(']');
        } else if (v instanceof Number || v instanceof Boolean) {
            sb.append(v);
        } else {
            sb.append('"').append(escapar(limpiar(String.valueOf(v)))).append('"');
        }
    }

    static String limpiar(String texto) {
        String t = VALOR_JWT.matcher(texto).replaceAll(OCULTO);
        t = VALOR_BEARER.matcher(t).replaceAll("Bearer " + OCULTO);
        return VALOR_TARJETA.matcher(t).replaceAll(OCULTO);
    }

    static String escapar(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
                }
            }
        }
        return sb.toString();
    }
}
