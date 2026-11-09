package edu.unbosque.marketplace.seguridad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.unbosque.marketplace.common.ReglaNegocioException;
import edu.unbosque.marketplace.config.PropiedadesApp;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** HU-002: el token que emite el login debe ser aceptado por JwtVerificador (el que usan los demás módulos). */
class JwtEmisorTest {

    private static final String SECRETO = "secreto-de-pruebas-con-mas-de-32-caracteres";
    private static final Instant AHORA = Instant.parse("2026-11-09T12:00:00Z");
    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private static PropiedadesApp props(String secreto) {
        return new PropiedadesApp(secreto, "x".repeat(40), BigDecimal.TEN, 15, "simulada", null, "cop",
                "America/Bogota");
    }

    private static JwtEmisor emisor(String secreto) {
        return new JwtEmisor(props(secreto), new ObjectMapper(), Clock.fixed(AHORA, ZoneOffset.UTC), 8);
    }

    private static JwtVerificador verificador(String secreto) {
        return new JwtVerificador(props(secreto), new ObjectMapper());
    }

    @Test
    void elVerificadorAceptaElTokenYLeeIdYRol() {
        var token = emisor(SECRETO).emitir(ID, "ANFITRION");

        Sesion s = verificador(SECRETO).verificar(token.valor(), AHORA.plusSeconds(60));

        assertEquals(ID, s.id());
        assertEquals(Sesion.Rol.ANFITRION, s.rol());
        assertEquals(AHORA.plus(Duration.ofHours(8)), token.expiraEn());
    }

    @Test
    void elTokenVencidoSeRechaza() {
        var token = emisor(SECRETO).emitir(ID, "HUESPED");

        assertThrows(ReglaNegocioException.class,
                () -> verificador(SECRETO).verificar(token.valor(), AHORA.plus(Duration.ofHours(9))));
    }

    @Test
    void unTokenFirmadoConOtroSecretoSeRechaza() {
        var token = emisor(SECRETO).emitir(ID, "HUESPED");

        assertThrows(ReglaNegocioException.class,
                () -> verificador("otro-secreto-distinto-con-mas-de-32-caracteres").verificar(token.valor(), AHORA));
    }

    @Test
    void sinSecretoSuficienteNoSeEmiteToken() {
        assertThrows(IllegalStateException.class, () -> emisor("corto").emitir(ID, "HUESPED"));
        assertThrows(IllegalStateException.class, () -> emisor(null).emitir(ID, "HUESPED"));
    }
}
