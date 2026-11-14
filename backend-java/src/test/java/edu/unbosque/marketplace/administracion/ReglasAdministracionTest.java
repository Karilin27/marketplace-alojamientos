package edu.unbosque.marketplace.administracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.unbosque.marketplace.administracion.AdminDtos.Tabla;
import edu.unbosque.marketplace.administracion.ReglasAdministracion.Accion;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** S6 y HU-003: reglas puras y generación de archivos (casos CP-03, CP-17, CP-18 y CP-21 de la página 19). */
class ReglasAdministracionTest {

    // ------------------------------------------------------------------ HU-018
    @Test
    void cp_17_01_aprobarPublicacionEnRevision() {
        assertEquals("ACTIVA", ReglasAdministracion.destino(Accion.APROBAR, "PENDIENTE_REVISION"));
    }

    @Test
    void cp_17_02_y_cp_17_03_pausarYBloquear() {
        assertEquals("PAUSADA", ReglasAdministracion.destino(Accion.PAUSAR, "ACTIVA"));
        assertEquals("BLOQUEADA", ReglasAdministracion.destino(Accion.BLOQUEAR, "ACTIVA"));
        assertEquals("PAUSADA", ReglasAdministracion.destino(Accion.DESBLOQUEAR, "BLOQUEADA"));
    }

    @Test
    void transicionesNoPermitidas() {
        assertNull(ReglasAdministracion.destino(Accion.BLOQUEAR, "BLOQUEADA"));
        assertNull(ReglasAdministracion.destino(Accion.DESBLOQUEAR, "ACTIVA"));
        assertNull(ReglasAdministracion.destino(Accion.APROBAR, "ACTIVA"));
        assertNull(ReglasAdministracion.destino(Accion.PAUSAR, "BORRADOR"));
    }

    @Test
    void cp_17_05_bloquearExigeMotivo() {
        assertTrue(ReglasAdministracion.exigeMotivo(Accion.BLOQUEAR));
        assertEquals(Accion.APROBAR, ReglasAdministracion.accion(" aprobar "));
        assertNull(ReglasAdministracion.accion("BORRAR"));
    }

    // ------------------------------------------------------------------ HU-022
    @Test
    void cp_18_02_restringirConMotivo() {
        assertTrue(ReglasAdministracion.erroresCambioEstado("RESTRINGIDO", "Incumplimiento", false, false).isEmpty());
        assertTrue(ReglasAdministracion.erroresCambioEstado("ACTIVO", null, false, false).isEmpty());
    }

    @Test
    void cp_18_04_restringirSinMotivoYCasosNoPermitidos() {
        assertEquals(1, ReglasAdministracion.erroresCambioEstado("RESTRINGIDO", " ", false, false).size());
        assertEquals(1, ReglasAdministracion.erroresCambioEstado("BORRADO", null, false, false).size());
        assertEquals(1, ReglasAdministracion.erroresCambioEstado("ACTIVO", null, true, false).size());
        assertEquals(1, ReglasAdministracion.erroresCambioEstado("RESTRINGIDO", "x", false, true).size());
    }

    // ------------------------------------------------------------------ HU-003
    @Test
    void cp_03_06_contrasenaCorta() {
        assertEquals(1, ReglasAdministracion.erroresNuevaContrasena("tok", "abc12").size());
        assertTrue(ReglasAdministracion.erroresNuevaContrasena("tok", "Nueva2026*").isEmpty());
        assertEquals(2, ReglasAdministracion.erroresNuevaContrasena(" ", null).size());
    }

    @Test
    void elTokenSeGuardaComoHash() {
        String h = RecuperacionService.hash("token-de-prueba");
        assertEquals(64, h.length());
        assertEquals(h, RecuperacionService.hash("token-de-prueba"));
    }

    // ------------------------------------------------------------------ HU-021
    @Test
    void cp_21_tiposYFormatos() {
        assertTrue(ReglasAdministracion.erroresReporte("RESERVAS", "PDF").isEmpty());
        assertTrue(ReglasAdministracion.erroresReporte("INGRESOS", "EXCEL").isEmpty());
        assertEquals(2, ReglasAdministracion.erroresReporte("VENTAS", "WORD").size());
    }

    private static final Tabla RESERVAS = new Tabla("Reservas", List.of("Alojamiento", "Total", "Estado"),
            List.of(List.of("Apartamento Chapinero", "710000.00", "CONFIRMADA"),
                    List.of("Cabaña Guatavita", "740000.00", "COMPLETADA")));

    @Test
    void cp_21_01_generaPdfValido() {
        byte[] pdf = ReporteService.pdf(RESERVAS, "Generado hoy");
        assertTrue(pdf.length > 500);
        assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.ISO_8859_1));
    }

    @Test
    void cp_21_02_generaExcelValido() {
        byte[] xlsx = ReporteService.excel(RESERVAS, "Generado hoy");
        assertTrue(xlsx.length > 500);
        assertEquals('P', (char) xlsx[0]);                      // los .xlsx son archivos ZIP («PK»)
        assertEquals('K', (char) xlsx[1]);
    }

    @Test
    void cp_21_03_reporteSinDatosSeGenera() {
        Tabla vacia = new Tabla("Demanda", List.of("Ciudad", "Reservas"), List.of());
        assertTrue(ReporteService.pdf(vacia, "hoy").length > 100);
        assertTrue(ReporteService.excel(vacia, "hoy").length > 100);
    }
}
