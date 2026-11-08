package edu.unbosque.marketplace.reservas.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.unbosque.marketplace.common.ReglaNegocioException;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Casos CAL-04 a CAL-06 y CP-14-xx (HU-015, RN-11, RN-18). */
class PoliticaAplicadaTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 20);

    private static BigDecimal m(String v) {
        return new BigDecimal(v).setScale(2);
    }

    @Test
    void cal04_moderadaConDiezDiasDevuelveTodo() {
        var p = PoliticaAplicada.Tipo.MODERADA.aplicada();
        assertEquals(m("710000"), p.calcularDevolucion(new BigDecimal("710000"), HOY, HOY.plusDays(10)));
    }

    @Test
    void cal05_moderadaConTresDiasNoDevuelve() {
        var p = PoliticaAplicada.Tipo.MODERADA.aplicada();
        assertEquals(m("0"), p.calcularDevolucion(new BigDecimal("710000"), HOY, HOY.plusDays(3)));
    }

    @Test
    void cal06_estrictaConVeinteDiasDevuelveLaMitad() {
        var p = PoliticaAplicada.Tipo.ESTRICTA.aplicada();
        assertEquals(m("350000"), p.calcularDevolucion(new BigDecimal("700000"), HOY, HOY.plusDays(20)));
    }

    @Test
    void limitesExactosDeAntelacion() {
        var flexible = PoliticaAplicada.Tipo.FLEXIBLE.aplicada();
        assertEquals(m("100"), flexible.calcularDevolucion(new BigDecimal("100"), HOY, HOY.plusDays(2)));
        assertEquals(m("0"), flexible.calcularDevolucion(new BigDecimal("100"), HOY, HOY.plusDays(1)));
        var estricta = PoliticaAplicada.Tipo.ESTRICTA.aplicada();
        assertEquals(m("50"), estricta.calcularDevolucion(new BigDecimal("100"), HOY, HOY.plusDays(14)));
        assertEquals(m("0"), estricta.calcularDevolucion(new BigDecimal("100"), HOY, HOY.plusDays(13)));
    }

    @Test
    void elMismoDiaDeLlegadaSePuedeCancelarSinDevolucion() {
        var p = PoliticaAplicada.Tipo.FLEXIBLE.aplicada();
        assertEquals(m("0"), p.calcularDevolucion(new BigDecimal("100"), HOY, HOY));
    }

    @Test
    void cp_14_04_laReservaConservaLosValoresDeSuPolitica() {
        // la reserva guarda (nombre, dias, %) al reservar; cambiar la política de la publicación no la afecta
        var alReservar = new PoliticaAplicada("MODERADA", 7, 100);
        assertEquals(m("710000"), alReservar.calcularDevolucion(new BigDecimal("710000"), HOY, HOY.plusDays(10)));
    }

    @Test
    void cp_14_06_noSePuedeCancelarDespuesDeLaLlegada() {
        var p = PoliticaAplicada.Tipo.FLEXIBLE.aplicada();
        ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                () -> p.calcularDevolucion(new BigDecimal("100"), HOY, HOY.minusDays(1)));
        assertEquals("CANCELACION_NO_PERMITIDA", e.codigo());
    }

    @Test
    void soloSeCancelaUnaReservaConfirmadaConLlegadaVigente() {
        assertTrue(EstadoReserva.CONFIRMADA.puedeCancelarse(HOY.plusDays(1), HOY));
        assertTrue(EstadoReserva.CONFIRMADA.puedeCancelarse(HOY, HOY));
        assertFalse(EstadoReserva.CONFIRMADA.puedeCancelarse(HOY.minusDays(1), HOY));
        assertFalse(EstadoReserva.RECHAZADA.puedeCancelarse(HOY.plusDays(5), HOY));
        assertFalse(EstadoReserva.PENDIENTE_PAGO.puedeCancelarse(HOY.plusDays(5), HOY));
        assertFalse(EstadoReserva.CANCELADA.puedeCancelarse(HOY.plusDays(5), HOY));
    }
}
