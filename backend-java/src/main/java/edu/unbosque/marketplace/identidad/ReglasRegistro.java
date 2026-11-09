package edu.unbosque.marketplace.identidad;

import edu.unbosque.marketplace.identidad.RegistroDtos.RegistroSolicitud;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reglas puras de validación del registro (HU-001). Sin Spring ni base de datos, para probarlas con JUnit.
 * El patrón de correo es el mismo que usa el formulario del frontend.
 */
public final class ReglasRegistro {

    private static final Pattern CORREO = Pattern.compile(
            "^[A-Za-z0-9_%+-]+(?:\\.[A-Za-z0-9_%+-]+)*@(?:[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?\\.)+[A-Za-z]{2,}$");

    /** CA-04: el rol ADMINISTRADOR no se puede elegir al registrarse. */
    static final Set<String> ROLES_PERMITIDOS = Set.of("HUESPED", "ANFITRION");

    static final int CONTRASENA_MIN = 8;
    /** BCrypt solo usa los primeros 72 bytes; más largo se rechaza para no truncar en silencio. */
    static final int CONTRASENA_MAX_BYTES = 72;

    private ReglasRegistro() {
    }

    /** Datos normalizados: nombre recortado, correo en minúsculas, rol en mayúsculas. La contraseña no se toca. */
    public record Datos(String nombre, String correo, String contrasena, String rol) {
    }

    public static Datos normalizar(RegistroSolicitud s) {
        return new Datos(
                recortar(s.nombre()),
                s.correo() == null ? null : s.correo().strip().toLowerCase(Locale.ROOT),
                s.contrasena(),
                s.rol() == null ? null : s.rol().strip().toUpperCase(Locale.ROOT));
    }

    /** Lista de mensajes que indican qué corregir; vacía si los datos son válidos. */
    public static List<String> errores(Datos d) {
        List<String> e = new ArrayList<>();
        if (d.nombre() == null || d.nombre().isEmpty()) {
            e.add("nombre: es obligatorio.");
        } else if (d.nombre().length() < 2 || d.nombre().length() > 100) {
            e.add("nombre: debe tener entre 2 y 100 caracteres.");
        }
        if (d.correo() == null || d.correo().isEmpty()) {
            e.add("correo: es obligatorio.");
        } else if (d.correo().length() > 254 || !CORREO.matcher(d.correo()).matches()) {
            e.add("correo: no tiene un formato válido (ejemplo: usuario@correo.com).");
        }
        if (d.contrasena() == null || d.contrasena().isBlank()) {
            e.add("contrasena: es obligatoria.");
        } else if (d.contrasena().length() < CONTRASENA_MIN) {
            e.add("contrasena: debe tener al menos 8 caracteres.");
        } else if (d.contrasena().getBytes(StandardCharsets.UTF_8).length > CONTRASENA_MAX_BYTES) {
            e.add("contrasena: no puede superar 72 caracteres.");
        }
        if (d.rol() == null || d.rol().isEmpty()) {
            e.add("rol: es obligatorio.");
        } else if (!ROLES_PERMITIDOS.contains(d.rol())) {
            e.add("rol: debe ser HUESPED o ANFITRION.");
        }
        return e;
    }

    private static String recortar(String s) {
        return s == null ? null : s.strip();
    }
}
