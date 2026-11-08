package edu.unbosque.marketplace.reservas.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.unbosque.marketplace.common.ReglaNegocioException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Vigencia de 30 minutos (RN-08, CP-10-07, CP-11-08) y firma de la cotización. */
class TokenCotizacionTest {

    private static final byte[] SECRETO = "secreto-de-prueba-de-al-menos-32-bytes!".getBytes(StandardCharsets.UTF_8);
    private static final Instant AHORA = Instant.parse("2026-11-12T15:00:00Z");

    private static TokenCotizacion.Datos datos(Instant expira) {
        return new TokenCotizacion.Datos("aloj-1",
                new RangoFechas(LocalDate.of(2026, 12, 12), LocalDate.of(2026, 12, 15)), 2,
                List.of("svc-2", "svc-1"), new BigDecimal("740000.00"), "user-1", expira);
    }

    @Test
    void ciclaEmitirYVerificar() {
        String token = TokenCotizacion.emitir(datos(AHORA.plus(TokenCotizacion.VIGENCIA)), SECRETO);
        TokenCotizacion.Datos d = TokenCotizacion.verificar(token, SECRETO, AHORA);
        assertEquals("aloj-1", d.idAlojamiento());
        assertEquals(2, d.huespedes());
        assertEquals(List.of("svc-1", "svc-2"), d.idsServicios());
        assertEquals(new BigDecimal("740000.00"), d.total());
        assertEquals("user-1", d.idHuesped());
    }

    @Test
    void vigenciaDeTreintaMinutos() {
        assertEquals(Duration.ofMinutes(30), TokenCotizacion.VIGENCIA);
        String token = TokenCotizacion.emitir(datos(AHORA.plus(TokenCotizacion.VIGENCIA)), SECRETO);
        TokenCotizacion.verificar(token, SECRETO, AHORA.plusSeconds(29 * 60));
        ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                () -> TokenCotizacion.verificar(token, SECRETO, AHORA.plusSeconds(31 * 60)));
        assertEquals("COTIZACION_VENCIDA", e.codigo());
        assertEquals(409, e.tipo().http());
    }

    @Test
    void rechazaUnTokenManipulado() {
        String token = TokenCotizacion.emitir(datos(AHORA.plus(TokenCotizacion.VIGENCIA)), SECRETO);
        String[] partes = token.split("\\.");
        String payload = new String(java.util.Base64.getUrlDecoder().decode(partes[0]), StandardCharsets.UTF_8)
                .replace("740000.00", "1.00");
        String falso = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + partes[1];
        ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                () -> TokenCotizacion.verificar(falso, SECRETO, AHORA));
        assertEquals("COTIZACION_INVALIDA", e.codigo());
    }

    @Test
    void rechazaOtroSecretoYBasura() {
        String token = TokenCotizacion.emitir(datos(AHORA.plus(TokenCotizacion.VIGENCIA)), SECRETO);
        byte[] otro = "otro-secreto-de-al-menos-32-bytes-xx".getBytes(StandardCharsets.UTF_8);
        assertThrows(ReglaNegocioException.class, () -> TokenCotizacion.verificar(token, otro, AHORA));
        assertThrows(ReglaNegocioException.class, () -> TokenCotizacion.verificar("abc", SECRETO, AHORA));
        assertThrows(ReglaNegocioException.class, () -> TokenCotizacion.verificar(null, SECRETO, AHORA));
        assertThrows(ReglaNegocioException.class, () -> TokenCotizacion.verificar("a.b.c", SECRETO, AHORA));
        assertThrows(ReglaNegocioException.class, () -> TokenCotizacion.verificar("!!!.???", SECRETO, AHORA));
    }

    @Test
    void sinServiciosAdicionales() {
        TokenCotizacion.Datos sin = new TokenCotizacion.Datos("aloj-1",
                new RangoFechas(LocalDate.of(2026, 12, 12), LocalDate.of(2026, 12, 15)), 1, List.of(),
                new BigDecimal("1.00"), "user-1", AHORA.plusSeconds(60));
        String token = TokenCotizacion.emitir(sin, SECRETO);
        assertEquals(List.of(), TokenCotizacion.verificar(token, SECRETO, AHORA).idsServicios());
    }
}
