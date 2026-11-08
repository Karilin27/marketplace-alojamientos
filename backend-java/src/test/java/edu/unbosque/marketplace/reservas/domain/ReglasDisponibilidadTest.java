package edu.unbosque.marketplace.reservas.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.unbosque.marketplace.common.ReglaNegocioException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** HU-008 CA-01, CA-03, CA-04 y CP-07-xx; RN-06 (no solapamiento). */
class ReglasDisponibilidadTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 10);

    private static Periodo p(int desde, int hasta, Periodo.Tipo t) {
        return new Periodo(HOY.plusDays(desde), HOY.plusDays(hasta), t);
    }

    private static RangoFechas r(int desde, int hasta) {
        return new RangoFechas(HOY.plusDays(desde), HOY.plusDays(hasta));
    }

    @Test
    void cp_07_01_registraPeriodoNoDisponible() {
        List<Periodo> ok = ReglasDisponibilidad.validar(List.of(p(20, 25, Periodo.Tipo.NO_DISPONIBLE)), HOY);
        assertEquals(1, ok.size());
    }

    @Test
    void cp_07_02_fechasInvalidasOPasadas() {
        assertEquals("FECHAS_INVALIDAS", assertThrows(ReglaNegocioException.class,
                () -> ReglasDisponibilidad.validar(List.of(p(10, 8, Periodo.Tipo.NO_DISPONIBLE)), HOY)).codigo());
        assertEquals("PERIODO_EN_EL_PASADO", assertThrows(ReglaNegocioException.class,
                () -> ReglasDisponibilidad.validar(List.of(p(-5, -2, Periodo.Tipo.NO_DISPONIBLE)), HOY)).codigo());
    }

    @Test
    void noSeRegistraReservado() {
        assertEquals("TIPO_PERIODO_INVALIDO", assertThrows(ReglaNegocioException.class,
                () -> ReglasDisponibilidad.validar(List.of(p(10, 12, Periodo.Tipo.RESERVADO)), HOY)).codigo());
    }

    @Test
    void periodosSuperpuestosSeRechazan() {
        assertEquals("PERIODOS_SUPERPUESTOS", assertThrows(ReglaNegocioException.class,
                () -> ReglasDisponibilidad.validar(List.of(p(10, 15, Periodo.Tipo.NO_DISPONIBLE),
                        p(14, 18, Periodo.Tipo.DISPONIBLE)), HOY)).codigo());
        // contiguos no se superponen: [10,15) y [15,18)
        ReglasDisponibilidad.validar(List.of(p(10, 15, Periodo.Tipo.NO_DISPONIBLE),
                p(15, 18, Periodo.Tipo.DISPONIBLE)), HOY);
    }

    @Test
    void cp_07_03_choqueConReservaConfirmada() {
        // reserva D+5 a D+8 · marcar D+6 a D+7 como no disponible
        assertEquals(1, ReglasDisponibilidad.choques(r(6, 7), List.of(r(5, 8))).size());
        assertEquals(0, ReglasDisponibilidad.choques(r(8, 10), List.of(r(5, 8))).size());
    }

    @Test
    void cp_11_02_solapamientoYNocheDeSalidaLibre() {
        List<RangoFechas> reservas = List.of(r(30, 33));
        assertFalse(ReglasDisponibilidad.libre(r(30, 33), List.of(), reservas));
        assertFalse(ReglasDisponibilidad.libre(r(32, 35), List.of(), reservas));
        assertTrue(ReglasDisponibilidad.libre(r(33, 36), List.of(), reservas));
        assertTrue(ReglasDisponibilidad.libre(r(27, 30), List.of(), reservas));
    }

    @Test
    void cp_10_06_periodoNoDisponibleBloquea() {
        List<Periodo> bloqueos = List.of(p(20, 25, Periodo.Tipo.NO_DISPONIBLE));
        assertFalse(ReglasDisponibilidad.libre(r(20, 22), bloqueos, List.of()));
        assertTrue(ReglasDisponibilidad.libre(r(25, 27), bloqueos, List.of()));
        // un periodo DISPONIBLE explícito no bloquea
        assertTrue(ReglasDisponibilidad.libre(r(20, 22), List.of(p(20, 25, Periodo.Tipo.DISPONIBLE)), List.of()));
    }
}
