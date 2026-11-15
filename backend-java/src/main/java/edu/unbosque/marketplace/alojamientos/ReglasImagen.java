package edu.unbosque.marketplace.alojamientos;

import java.util.Locale;

/**
 * Reglas puras de la subida de imágenes (HU-005 CA-03, cambio D-21). Sin Spring, para probarlas con JUnit.
 * El formato se decide por los primeros bytes del archivo ("número mágico") y no por el nombre ni por el
 * Content-Type que manda el navegador, que el usuario puede cambiar.
 */
public final class ReglasImagen {

    /** Tamaño máximo por imagen: 5 MB. */
    public static final long MAX_BYTES = 5L * 1024 * 1024;

    /** Formatos aceptados: extensión y tipo MIME. */
    public enum Formato {
        JPG("jpg", "image/jpeg"), PNG("png", "image/png"), WEBP("webp", "image/webp");

        private final String extension;
        private final String mime;

        Formato(String extension, String mime) {
            this.extension = extension;
            this.mime = mime;
        }

        public String extension() { return extension; }

        public String mime() { return mime; }
    }

    private ReglasImagen() {
    }

    /** Devuelve el formato según la firma del archivo, o null si no es JPG, PNG ni WEBP. */
    public static Formato detectar(byte[] b) {
        if (b == null || b.length < 12) {
            return null;
        }
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return Formato.JPG;
        }
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return Formato.PNG;
        }
        if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return Formato.WEBP;
        }
        return null;
    }

    /** true si el tamaño supera el máximo permitido. */
    public static boolean excedeTamano(long bytes) {
        return bytes > MAX_BYTES;
    }

    /** Ruta del objeto en el bucket: carpeta del anfitrión + nombre aleatorio + extensión. */
    public static String ruta(String idAnfitrion, String nombreAleatorio, Formato formato) {
        return idAnfitrion.toLowerCase(Locale.ROOT) + "/" + nombreAleatorio + "." + formato.extension();
    }
}
