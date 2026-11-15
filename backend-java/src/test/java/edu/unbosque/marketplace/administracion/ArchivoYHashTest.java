package edu.unbosque.marketplace.administracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.unbosque.marketplace.administracion.ReporteService.Archivo;
import org.junit.jupiter.api.Test;

/** Hallazgos de SonarCloud: igualdad del archivo exportado (java:S6218) y hash del token (javabugs:S2259). */
class ArchivoYHashTest {

    @Test
    void archivosConElMismoContenidoSonIguales() {
        Archivo a = new Archivo(new byte[] {1, 2, 3}, "application/pdf", "reporte.pdf");
        Archivo b = new Archivo(new byte[] {1, 2, 3}, "application/pdf", "reporte.pdf");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(a, a);
    }

    @Test
    void archivosDistintosNoSonIguales() {
        Archivo a = new Archivo(new byte[] {1, 2, 3}, "application/pdf", "reporte.pdf");
        assertNotEquals(a, new Archivo(new byte[] {9}, "application/pdf", "reporte.pdf"));
        assertNotEquals(a, new Archivo(new byte[] {1, 2, 3}, "text/plain", "reporte.pdf"));
        assertNotEquals(a, new Archivo(new byte[] {1, 2, 3}, "application/pdf", "otro.pdf"));
        assertNotEquals(a, "reporte.pdf");
    }

    @Test
    void toStringNoVuelcaElContenido() {
        Archivo a = new Archivo(new byte[] {1, 2, 3}, "application/pdf", "reporte.pdf");
        assertEquals("Archivo[reporte.pdf, application/pdf, 3 bytes]", a.toString());
        assertTrue(new Archivo(null, "x", "y").toString().contains("0 bytes"));
    }

    @Test
    void hashDelTokenEsSha256Hexadecimal() {
        assertEquals("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08", RecuperacionService.hash("test"));
        assertEquals(64, RecuperacionService.hash("otro-token").length());
    }

    @Test
    void hashSinTokenSeRechaza() {
        assertThrows(IllegalArgumentException.class, () -> RecuperacionService.hash(null));
    }
}
