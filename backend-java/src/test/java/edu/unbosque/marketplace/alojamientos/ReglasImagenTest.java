package edu.unbosque.marketplace.alojamientos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.unbosque.marketplace.alojamientos.ReglasImagen.Formato;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** D-21: detección del formato por la firma del archivo y límites de la subida de imágenes. */
class ReglasImagenTest {

    static byte[] jpg() {
        byte[] b = new byte[32];
        b[0] = (byte) 0xFF;
        b[1] = (byte) 0xD8;
        b[2] = (byte) 0xFF;
        b[3] = (byte) 0xE0;
        return b;
    }

    static byte[] png() {
        byte[] b = new byte[32];
        byte[] firma = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(firma, 0, b, 0, firma.length);
        return b;
    }

    static byte[] webp() {
        byte[] b = Arrays.copyOf("RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1), 32);
        return b;
    }

    @Test
    void detectaJpgPngYWebp() {
        assertEquals(Formato.JPG, ReglasImagen.detectar(jpg()));
        assertEquals(Formato.PNG, ReglasImagen.detectar(png()));
        assertEquals(Formato.WEBP, ReglasImagen.detectar(webp()));
        assertEquals("image/jpeg", Formato.JPG.mime());
        assertEquals("webp", Formato.WEBP.extension());
    }

    @Test
    void rechazaOtrosFormatosOArchivosCortos() {
        assertNull(ReglasImagen.detectar(null));
        assertNull(ReglasImagen.detectar(new byte[5]));
        assertNull(ReglasImagen.detectar(Arrays.copyOf("GIF89a".getBytes(StandardCharsets.US_ASCII), 32)));
        assertNull(ReglasImagen.detectar(Arrays.copyOf("%PDF-1.7".getBytes(StandardCharsets.US_ASCII), 32)));
        assertNull(ReglasImagen.detectar(Arrays.copyOf("RIFF\0\0\0\0WAVEfmt ".getBytes(StandardCharsets.ISO_8859_1), 32)));
        byte[] pngIncompleto = png();
        pngIncompleto[4] = 0;
        assertNull(ReglasImagen.detectar(pngIncompleto));
        byte[] casiJpg = jpg();
        casiJpg[2] = 0;
        assertNull(ReglasImagen.detectar(casiJpg));
    }

    @Test
    void limiteDeCincoMegas() {
        assertFalse(ReglasImagen.excedeTamano(ReglasImagen.MAX_BYTES));
        assertTrue(ReglasImagen.excedeTamano(ReglasImagen.MAX_BYTES + 1));
    }

    @Test
    void rutaEnLaCarpetaDelAnfitrion() {
        assertEquals("abc-123/foto.png", ReglasImagen.ruta("ABC-123", "foto", Formato.PNG));
    }
}
