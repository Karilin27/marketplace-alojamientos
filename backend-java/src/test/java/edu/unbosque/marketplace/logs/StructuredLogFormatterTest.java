package edu.unbosque.marketplace.logs;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** HU-026 CA-02 (estructura JSON) y CA-04 (sin datos sensibles); CP-T-02 y CP-T-04. */
class StructuredLogFormatterTest {

    private static final Instant T = Instant.parse("2026-11-12T15:00:00Z");

    @Test
    void cp_T_02_estructuraJson() {
        String json = StructuredLogFormatter.formatear(T, NivelLog.INFO, "RESERVA_CONFIRMADA", "user-1",
                "reserva", "res-1", "EXITO", Map.of("total", 710000));
        assertTrue(json.startsWith("{\"timestamp\":\"2026-11-12T15:00:00Z\""));
        assertTrue(json.contains("\"nivel\":\"INFO\""));
        assertTrue(json.contains("\"evento\":\"RESERVA_CONFIRMADA\""));
        assertTrue(json.contains("\"usuario\":\"user-1\""));
        assertTrue(json.contains("\"entidad\":\"reserva\""));
        assertTrue(json.contains("\"idEntidad\":\"res-1\""));
        assertTrue(json.contains("\"resultado\":\"EXITO\""));
        assertTrue(json.contains("\"detalle\":{\"total\":710000}"));
        assertTrue(json.endsWith("}"));
    }

    @Test
    void cp_T_04_noEscribeContrasenasTokensNiTarjetas() {
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("password", "Clave2026*");
        detalle.put("contraseña", "Clave2026*");
        detalle.put("token", "abc123");
        detalle.put("Authorization", "Bearer eyJhbGciOi.eyJzdWIiOi.firma");
        detalle.put("tarjeta", "4242 4242 4242 4242");
        detalle.put("metodoPago", "pm_card_visa");
        detalle.put("nota", "pagó con 4242424242424242 y token eyJhbGciOiJI.eyJzdWIiOiJ1.abc-def");
        detalle.put("anidado", Map.of("secret", "x", "ok", "visible"));
        detalle.put("lista", List.of("4000-0000-0000-0002"));
        String json = StructuredLogFormatter.formatear(T, NivelLog.INFO, "PAGO", "u", "pago", "p-1", "EXITO", detalle);
        assertFalse(json.contains("Clave2026"));
        assertFalse(json.contains("abc123"));
        assertFalse(json.contains("eyJ"));
        assertFalse(json.contains("4242"));
        assertFalse(json.contains("4000"));
        assertFalse(json.contains("pm_card_visa"));
        assertTrue(json.contains("\"ok\":\"visible\""));
    }

    @Test
    void noAlteraIdentificadoresUuidNiFechas() {
        String uuid = "550e8400-e29b-41d4-a716-446655440000";
        String json = StructuredLogFormatter.formatear(T, NivelLog.INFO, "E", uuid, "reserva", uuid, "EXITO",
                Map.of("fechaLlegada", "2026-12-12"));
        assertTrue(json.contains("\"usuario\":\"" + uuid + "\""));
        assertTrue(json.contains("2026-12-12"));
        assertTrue(json.contains("2026-11-12T15:00:00Z"));
    }

    @Test
    void escapaCaracteresEspecialesYAceptaNulos() {
        String json = StructuredLogFormatter.formatear(T, NivelLog.ERROR, "ERROR", null, null, null, "FALLO",
                Map.of("detalle", "línea1\n\"cita\" \\ fin"));
        assertTrue(json.contains("\"usuario\":null"));
        assertTrue(json.contains("\\n"));
        assertTrue(json.contains("\\\"cita\\\""));
        assertTrue(json.contains("\"nivel\":\"ERROR\""));
        String vacio = StructuredLogFormatter.formatear(T, NivelLog.INFO, "E", "u", "e", "1", "OK", null);
        assertTrue(vacio.contains("\"detalle\":{}"));
    }
}
