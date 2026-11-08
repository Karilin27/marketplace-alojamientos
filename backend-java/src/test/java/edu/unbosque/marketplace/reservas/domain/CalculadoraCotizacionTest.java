package edu.unbosque.marketplace.reservas.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.unbosque.marketplace.common.ReglaNegocioException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Casos CAL-01 a CAL-03 y CP-10-xx de la página 19 de Confluence (HU-011, RN-07, RN-17). */
class CalculadoraCotizacionTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 10);
    // A-01: $200.000 por noche, limpieza $50.000, 10 % de descuento desde 7 noches, capacidad 4
    private static final DatosTarifa A01 = tarifa("10");
    private static final ServicioAdicional DESAYUNO =
            new ServicioAdicional("svc-1", "Desayuno", new BigDecimal("30000"));

    private static DatosTarifa tarifa(String pctServicio) {
        return new DatosTarifa(new BigDecimal("200000"), new BigDecimal("50000"), new BigDecimal("10"), 7,
                new BigDecimal(pctServicio), 4);
    }

    private static BigDecimal m(String v) {
        return new BigDecimal(v).setScale(2);
    }

    private static RangoFechas noches(int desde, int n) {
        return new RangoFechas(HOY.plusDays(desde), HOY.plusDays(desde + n));
    }

    @Test
    void cal01_tresNochesSinDescuento() {
        Cotizacion c = CalculadoraCotizacion.calcular(A01, noches(30, 3), 2, List.of());
        assertEquals(3L, c.noches());
        assertEquals(m("600000"), c.subtotal());
        assertEquals(m("0"), c.descuento());
        assertEquals(m("60000"), c.tarifaServicio());
        assertEquals(m("50000"), c.tarifaLimpieza());
        assertEquals(m("710000"), c.total());
    }

    @Test
    void cal02_conServicioAdicionalDesayuno() {
        Cotizacion c = CalculadoraCotizacion.calcular(A01, noches(30, 3), 2, List.of(DESAYUNO));
        assertEquals(m("740000"), c.total());
        assertEquals(1, c.serviciosAdicionales().size());
        assertEquals(m("30000"), c.serviciosAdicionales().get(0).costo());
    }

    @Test
    void cal03_sieteNochesAplicaDescuento() {
        Cotizacion c = CalculadoraCotizacion.calcular(A01, noches(40, 7), 2, List.of());
        assertEquals(m("1400000"), c.subtotal());
        assertEquals(m("140000"), c.descuento());
        assertEquals(m("126000"), c.tarifaServicio());
        assertEquals(m("1436000"), c.total());
    }

    @Test
    void seisNochesNoAplicaDescuento() {
        Cotizacion c = CalculadoraCotizacion.calcular(A01, noches(40, 6), 2, List.of());
        assertEquals(m("0"), c.descuento());
    }

    @Test
    void cp_05_11_tarifaDeServicioConfigurableAl12porCiento() {
        Cotizacion c = CalculadoraCotizacion.calcular(tarifa("12"), noches(30, 3), 2, List.of());
        assertEquals(m("72000"), c.tarifaServicio());
        assertEquals(m("722000"), c.total());
    }

    @Test
    void cp_10_05_capacidadExcedida() {
        ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                () -> CalculadoraCotizacion.calcular(A01, noches(30, 3), 5, List.of()));
        assertEquals("CAPACIDAD_EXCEDIDA", e.codigo());
        assertEquals(422, e.tipo().http());
    }

    @Test
    void cp_10_04_fechasInvalidas() {
        ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                () -> RangoFechas.validado(HOY.plusDays(10), HOY.plusDays(8), HOY));
        assertEquals("FECHAS_INVALIDAS", e.codigo());
        assertThrows(ReglaNegocioException.class,
                () -> RangoFechas.validado(HOY.plusDays(10), HOY.plusDays(10), HOY));
        assertThrows(ReglaNegocioException.class,
                () -> RangoFechas.validado(HOY.minusDays(1), HOY.plusDays(2), HOY));
    }

    @Test
    void huespedesInvalidos() {
        ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                () -> CalculadoraCotizacion.calcular(A01, noches(30, 3), 0, List.of()));
        assertEquals(400, e.tipo().http());
    }

    @Test
    void redondeaADosDecimales() {
        DatosTarifa t = new DatosTarifa(new BigDecimal("100.05"), new BigDecimal("0"), new BigDecimal("0"), 0,
                new BigDecimal("10"), 2);
        Cotizacion c = CalculadoraCotizacion.calcular(t, noches(5, 1), 1, List.of());
        assertEquals(m("100.05"), c.subtotal());
        assertEquals(m("10.01"), c.tarifaServicio());
        assertEquals(m("110.06"), c.total());
    }
}
