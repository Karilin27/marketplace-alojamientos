package edu.unbosque.marketplace.identidad;

import edu.unbosque.marketplace.identidad.PerfilDtos.PerfilSolicitud;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Reglas puras de validación del perfil (HU-004, CA-03). Sin Spring ni base de datos, para probarlas con JUnit.
 * Supuesto a confirmar con el Product Owner: solo el nombre es obligatorio; teléfono, documento, país y foto son
 * opcionales, pero si se envían deben tener un formato válido.
 */
public final class ReglasPerfil {

    private static final Pattern TELEFONO = Pattern.compile("^\\+?[0-9]{7,15}$");
    private static final Pattern DOCUMENTO = Pattern.compile("^[A-Za-z0-9-]{5,20}$");
    private static final Pattern PAIS = Pattern.compile("^\\p{L}[\\p{L} .'-]{1,59}$");

    private ReglasPerfil() {
    }

    /** Datos ya recortados; los opcionales vacíos pasan a null. */
    public record Datos(String nombre, String telefono, String documento, String pais, String fotoUrl) {
    }

    public static Datos normalizar(PerfilSolicitud s) {
        return new Datos(recortar(s.nombre()), vacioANulo(s.telefono()), vacioANulo(s.documento()),
                vacioANulo(s.pais()), vacioANulo(s.fotoUrl()));
    }

    /** Lista de mensajes que indican qué corregir; vacía si los datos son válidos. */
    public static List<String> errores(Datos d) {
        List<String> e = new ArrayList<>();
        if (d.nombre() == null || d.nombre().isEmpty()) {
            e.add("nombre: es obligatorio.");
        } else if (d.nombre().length() < 2 || d.nombre().length() > 100) {
            e.add("nombre: debe tener entre 2 y 100 caracteres.");
        }
        if (d.telefono() != null && !TELEFONO.matcher(d.telefono().replaceAll("[ -]", "")).matches()) {
            e.add("telefono: use entre 7 y 15 dígitos, con + opcional al inicio.");
        }
        if (d.documento() != null && !DOCUMENTO.matcher(d.documento()).matches()) {
            e.add("documento: use entre 5 y 20 letras, números o guiones.");
        }
        if (d.pais() != null && !PAIS.matcher(d.pais()).matches()) {
            e.add("pais: use solo letras, entre 2 y 60 caracteres.");
        }
        if (d.fotoUrl() != null && !urlValida(d.fotoUrl())) {
            e.add("fotoUrl: debe ser una dirección http o https de hasta 500 caracteres.");
        }
        return e;
    }

    private static boolean urlValida(String texto) {
        if (texto.length() > 500) {
            return false;
        }
        try {
            URI u = URI.create(texto);
            return ("http".equalsIgnoreCase(u.getScheme()) || "https".equalsIgnoreCase(u.getScheme()))
                    && u.getHost() != null;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static String recortar(String s) {
        return s == null ? null : s.strip();
    }

    private static String vacioANulo(String s) {
        String r = recortar(s);
        return r == null || r.isEmpty() ? null : r;
    }
}
