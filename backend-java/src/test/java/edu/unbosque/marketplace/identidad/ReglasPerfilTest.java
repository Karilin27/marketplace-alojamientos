package edu.unbosque.marketplace.identidad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.unbosque.marketplace.identidad.PerfilDtos.PerfilSolicitud;
import java.util.List;
import org.junit.jupiter.api.Test;

/** HU-004 CA-02 y CA-03: validación del perfil. */
class ReglasPerfilTest {

    private static List<String> errores(String nombre, String tel, String doc, String pais, String foto) {
        return ReglasPerfil.errores(ReglasPerfil.normalizar(new PerfilSolicitud(nombre, tel, doc, pais, foto)));
    }

    @Test
    void perfilCompletoYValidoNoTieneErrores() {
        assertTrue(errores("Ana Pérez", "+57 300 123 4567", "CC-1020304050", "Colombia",
                "https://cdn.ejemplo.com/ana.png").isEmpty());
    }

    @Test
    void soloElNombreEsObligatorio() {
        assertTrue(errores("Ana", null, null, null, null).isEmpty());
        assertTrue(errores("Ana", "  ", "", " ", "").isEmpty());
    }

    @Test
    void nombreVacioONuloSeRechazaIndicandoElCampo() {
        assertEquals(List.of("nombre: es obligatorio."), errores("   ", null, null, null, null));
        assertEquals(List.of("nombre: es obligatorio."), errores(null, null, null, null, null));
    }

    @Test
    void nombreDemasiadoCortoOLargo() {
        assertEquals(1, errores("A", null, null, null, null).size());
        assertEquals(1, errores("x".repeat(101), null, null, null, null).size());
    }

    @Test
    void telefonoInvalidoSeRechaza() {
        assertEquals(1, errores("Ana", "12345", null, null, null).size());
        assertEquals(1, errores("Ana", "abc1234567", null, null, null).size());
        assertEquals(1, errores("Ana", "1".repeat(16), null, null, null).size());
    }

    @Test
    void telefonoConEspaciosYGuionesSeAcepta() {
        assertTrue(errores("Ana", "300-123 4567", null, null, null).isEmpty());
    }

    @Test
    void documentoPaisYFotoInvalidosSeRechazanTodosAlMismoTiempo() {
        var e = errores("Ana", null, "ab", "12", "ftp://x.com/a.png");
        assertEquals(3, e.size());
        assertTrue(e.get(0).startsWith("documento"));
        assertTrue(e.get(1).startsWith("pais"));
        assertTrue(e.get(2).startsWith("fotoUrl"));
    }

    @Test
    void fotoSinHostOMuyLargaSeRechaza() {
        assertEquals(1, errores("Ana", null, null, null, "https://").size());
        assertEquals(1, errores("Ana", null, null, null, "https://a.com/" + "x".repeat(500)).size());
    }

    @Test
    void normalizarRecortaYConvierteVaciosEnNulos() {
        var d = ReglasPerfil.normalizar(new PerfilSolicitud("  Ana  ", "  ", " CC-12345 ", "", null));
        assertEquals("Ana", d.nombre());
        assertNull(d.telefono());
        assertEquals("CC-12345", d.documento());
        assertNull(d.pais());
        assertNull(d.fotoUrl());
    }
}
