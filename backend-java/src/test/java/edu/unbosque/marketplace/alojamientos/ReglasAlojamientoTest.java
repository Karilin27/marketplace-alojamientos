package edu.unbosque.marketplace.alojamientos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.unbosque.marketplace.alojamientos.AlojamientoDtos.AlojamientoSolicitud;
import edu.unbosque.marketplace.alojamientos.AlojamientoDtos.ServicioAdicionalSolicitud;
import edu.unbosque.marketplace.alojamientos.AlojamientoDtos.TarifasSolicitud;
import edu.unbosque.marketplace.alojamientos.AlojamientoRepositorio.Filtros;
import edu.unbosque.marketplace.alojamientos.ReglasAlojamiento.Datos;
import edu.unbosque.marketplace.alojamientos.ReglasAlojamiento.Transicion;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Reglas de S2: HU-005, HU-006, HU-007, HU-009, HU-023 y HU-024 (casos de prueba de la página 19). */
class ReglasAlojamientoTest {

    private static final UUID MODERADA = UUID.randomUUID();

    /** A-01 de la página 19: Apartamento Chapinero, Bogotá, capacidad 4, $200.000 por noche. */
    private static AlojamientoSolicitud a01() {
        return new AlojamientoSolicitud("Apartamento Chapinero", "Cerca a la zona G", "Apartamento", "Bogotá",
                "Calle 60 # 5-20", 4, new BigDecimal("200000"), List.of("WiFi", "Cocina"), "No fumar",
                List.of("https://img.ejemplo.com/1.jpg", "https://img.ejemplo.com/2.jpg",
                        "https://img.ejemplo.com/3.jpg"), MODERADA);
    }

    private static Datos datos(AlojamientoSolicitud s) {
        return ReglasAlojamiento.normalizar(s);
    }

    // ------------------------------------------------------------------ HU-005

    @Test
    void cp_05_01_publicacionCompletaEsValida() {
        Datos d = datos(a01());
        assertTrue(ReglasAlojamiento.errores(d).isEmpty());
        assertEquals("APARTAMENTO", d.tipo());
    }

    @Test
    void normalizaEspaciosTildesYRepetidos() {
        Datos d = datos(new AlojamientoSolicitud("  Cabaña   Guatavita ", "Vista al embalse", "cabaña", "Guatavita",
                "Km 3", 6, new BigDecimal("300000"), List.of("WiFi", " WiFi ", ""), null, null, null));
        assertEquals("Cabaña Guatavita", d.titulo());
        assertEquals("CABANA", d.tipo());
        assertEquals(List.of("WiFi"), d.servicios());
        assertTrue(d.imagenes().isEmpty());
    }

    @Test
    void cp_05_02_capacidadOPrecioInvalidos() {
        Datos d = datos(new AlojamientoSolicitud("Casa", "Desc", "CASA", "Bogotá", "Cra 1", 0,
                new BigDecimal("-100"), null, null, null, null));
        List<String> e = ReglasAlojamiento.errores(d);
        assertEquals(2, e.size());
        assertTrue(e.get(0).startsWith("capacidad"));
        assertTrue(e.get(1).startsWith("precioPorNoche"));
    }

    @Test
    void camposObligatoriosVaciosYTipoInvalido() {
        Datos d = datos(new AlojamientoSolicitud(" ", null, "CASTILLO", null, null, 2, BigDecimal.ONE, null, null,
                List.of("no-es-url"), null));
        List<String> e = ReglasAlojamiento.errores(d);
        assertTrue(e.stream().anyMatch(x -> x.startsWith("titulo")));
        assertTrue(e.stream().anyMatch(x -> x.startsWith("descripcion")));
        assertTrue(e.stream().anyMatch(x -> x.startsWith("tipo")));
        assertTrue(e.stream().anyMatch(x -> x.startsWith("imagenes")));
    }

    // ------------------------------------------------------------------ HU-007 y HU-023 CA-03

    @Test
    void cp_06_01_publicacionCompletaPuedeActivarse() {
        assertTrue(ReglasAlojamiento.faltantesParaActivar(datos(a01())).isEmpty());
    }

    @Test
    void cp_06_02_a02SinImagenesNiPoliticaNoSeActiva() {
        Datos a02 = datos(new AlojamientoSolicitud("Casa Salitre", "Casa familiar", "CASA", "Bogotá", "Cra 68",
                5, new BigDecimal("250000"), null, null, null, null));
        assertEquals(List.of("imagenes (al menos una)", "politicaCancelacion"),
                ReglasAlojamiento.faltantesParaActivar(a02));
    }

    @Test
    void transicionesDelAnfitrion() {
        assertEquals(Transicion.PERMITIDA, ReglasAlojamiento.transicionAnfitrion("BORRADOR", "ACTIVA"));
        assertEquals(Transicion.PERMITIDA, ReglasAlojamiento.transicionAnfitrion("PAUSADA", "ACTIVA"));
        assertEquals(Transicion.PERMITIDA, ReglasAlojamiento.transicionAnfitrion("ACTIVA", "PAUSADA"));    // CP-06-03
        assertEquals(Transicion.SIN_CAMBIO, ReglasAlojamiento.transicionAnfitrion("ACTIVA", "ACTIVA"));
        assertEquals(Transicion.BLOQUEADA_SOLO_ADMIN,
                ReglasAlojamiento.transicionAnfitrion("BLOQUEADA", "ACTIVA"));                           // CP-06-04
        assertEquals(Transicion.EN_REVISION,
                ReglasAlojamiento.transicionAnfitrion("PENDIENTE_REVISION", "ACTIVA"));                  // CP-06-05
        assertEquals(Transicion.NO_PERMITIDA, ReglasAlojamiento.transicionAnfitrion("BORRADOR", "PAUSADA"));
    }

    // ------------------------------------------------------------------ HU-006 CA-06

    @Test
    void cp_05_07_cambiarTituloEsCambioCritico() {
        Datos antes = datos(a01());
        AlojamientoSolicitud s = a01();
        Datos otroTitulo = datos(new AlojamientoSolicitud("Apartamento Chapinero Alto", s.descripcion(), s.tipo(),
                s.ciudad(), s.direccion(), s.capacidad(), s.precioPorNoche(), s.servicios(), s.reglas(),
                s.imagenes(), s.idPoliticaCancelacion()));
        assertTrue(ReglasAlojamiento.cambiaCamposCriticos(antes, otroTitulo));
    }

    @Test
    void cp_05_08_cambiarPrecioNoEsCambioCritico() {
        Datos antes = datos(a01());
        AlojamientoSolicitud s = a01();
        Datos otroPrecio = datos(new AlojamientoSolicitud(s.titulo(), s.descripcion(), s.tipo(), s.ciudad(),
                s.direccion(), s.capacidad(), new BigDecimal("250000"), s.servicios(), s.reglas(), s.imagenes(),
                s.idPoliticaCancelacion()));
        assertFalse(ReglasAlojamiento.cambiaCamposCriticos(antes, otroPrecio));
    }

    // ------------------------------------------------------------------ HU-024

    @Test
    void cp_05_04_tarifasDeA01SonValidas() {
        var t = ReglasAlojamiento.normalizarTarifas(new TarifasSolicitud(new BigDecimal("50000"),
                new BigDecimal("10"), 7, List.of(new ServicioAdicionalSolicitud("Desayuno", new BigDecimal("30000"), null))));
        assertTrue(ReglasAlojamiento.erroresTarifas(t).isEmpty());
        assertTrue(t.adicionales().get(0).activo());
    }

    @Test
    void cp_05_05_tarifaNegativaYServicioRepetido() {
        var t = ReglasAlojamiento.normalizarTarifas(new TarifasSolicitud(new BigDecimal("-50000"),
                new BigDecimal("120"), 7, List.of(new ServicioAdicionalSolicitud("Desayuno", BigDecimal.ONE, true),
                        new ServicioAdicionalSolicitud("desayuno", new BigDecimal("-1"), true))));
        List<String> e = ReglasAlojamiento.erroresTarifas(t);
        assertTrue(e.stream().anyMatch(x -> x.startsWith("tarifaLimpieza")));
        assertTrue(e.stream().anyMatch(x -> x.startsWith("descuentoPct")));
        assertTrue(e.stream().anyMatch(x -> x.contains("repetido")));
        assertTrue(e.stream().anyMatch(x -> x.endsWith("costo: no puede ser negativo")));
    }

    @Test
    void descuentoSinMinimoDeNochesEsInvalido() {
        var t = ReglasAlojamiento.normalizarTarifas(new TarifasSolicitud(null, new BigDecimal("10"), null, null));
        assertEquals(1, ReglasAlojamiento.erroresTarifas(t).size());
    }

    @Test
    void cp_05_11_tarifaDeServicioEntre0y100() {
        assertTrue(ReglasAlojamiento.tarifaServicioValida(new BigDecimal("12")));
        assertFalse(ReglasAlojamiento.tarifaServicioValida(new BigDecimal("101")));
        assertFalse(ReglasAlojamiento.tarifaServicioValida(null));
    }

    // ------------------------------------------------------------------ HU-009 CA-04

    @Test
    void cp_08_04_filtrosInvalidos() {
        LocalDate hoy = LocalDate.of(2026, 11, 13);
        var fechasIguales = new Filtros("Bogotá", hoy.plusDays(30), hoy.plusDays(30), 2, null, null, null, List.of());
        assertFalse(BusquedaService.errores(fechasIguales, hoy).isEmpty());
        var preciosCruzados = new Filtros("Bogotá", null, null, null, null, new BigDecimal("300000"),
                new BigDecimal("100000"), List.of());
        assertFalse(BusquedaService.errores(preciosCruzados, hoy).isEmpty());
        var valida = new Filtros("Bogotá", hoy.plusDays(30), hoy.plusDays(33), 2, "APARTAMENTO",
                new BigDecimal("100000"), new BigDecimal("300000"), List.of("WiFi"));
        assertTrue(BusquedaService.errores(valida, hoy).isEmpty());
    }
}
