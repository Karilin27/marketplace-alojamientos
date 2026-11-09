package edu.unbosque.marketplace.identidad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.unbosque.marketplace.identidad.RegistroDtos.RegistroSolicitud;
import java.util.List;
import org.junit.jupiter.api.Test;

/** HU-001: validación de los datos de registro. */
class ReglasRegistroTest {

    private static List<String> errores(String nombre, String correo, String contrasena, String rol) {
        return ReglasRegistro.errores(ReglasRegistro.normalizar(new RegistroSolicitud(nombre, correo, contrasena, rol)));
    }

    @Test
    void registroCompletoYValidoNoTieneErrores() {
        assertTrue(errores("Ana Pérez", "ana@correo.com", "Secreta123", "HUESPED").isEmpty());
        assertTrue(errores("Luis", "luis.gomez@empresa.com.co", "Secreta123", "anfitrion").isEmpty());
    }

    @Test
    void normalizaCorreoNombreYRolPeroNoLaContrasena() {
        var d = ReglasRegistro.normalizar(new RegistroSolicitud("  Ana  ", " Ana@Correo.COM ", " Clave 123 ", " huesped "));
        assertEquals("Ana", d.nombre());
        assertEquals("ana@correo.com", d.correo());
        assertEquals("HUESPED", d.rol());
        assertEquals(" Clave 123 ", d.contrasena());
    }

    @Test
    void camposVaciosSeRechazanTodosIndicandoElCampo() {
        var e = errores(" ", "", null, "");
        assertEquals(4, e.size());
        assertTrue(e.get(0).startsWith("nombre"));
        assertTrue(e.get(1).startsWith("correo"));
        assertTrue(e.get(2).startsWith("contrasena"));
        assertTrue(e.get(3).startsWith("rol"));
    }

    @Test
    void correoConFormatoInvalidoSeRechaza() {
        assertEquals(1, errores("Ana", "ana@correo", "Secreta123", "HUESPED").size());
        assertEquals(1, errores("Ana", "ana correo@x.com", "Secreta123", "HUESPED").size());
        assertEquals(1, errores("Ana", "@correo.com", "Secreta123", "HUESPED").size());
    }

    @Test
    void contrasenaCortaOMuyLargaSeRechaza() {
        assertEquals(List.of("contrasena: debe tener al menos 8 caracteres."),
                errores("Ana", "ana@correo.com", "1234567", "HUESPED"));
        assertEquals(List.of("contrasena: no puede superar 72 caracteres."),
                errores("Ana", "ana@correo.com", "x".repeat(73), "HUESPED"));
    }

    @Test
    void noSePuedeRegistrarComoAdministradorNiConUnRolInexistente() {
        assertEquals(List.of("rol: debe ser HUESPED o ANFITRION."),
                errores("Ana", "ana@correo.com", "Secreta123", "ADMINISTRADOR"));
        assertEquals(1, errores("Ana", "ana@correo.com", "Secreta123", "OTRO").size());
    }

    @Test
    void nombreDemasiadoCortoOLargo() {
        assertEquals(1, errores("A", "ana@correo.com", "Secreta123", "HUESPED").size());
        assertEquals(1, errores("x".repeat(101), "ana@correo.com", "Secreta123", "HUESPED").size());
    }
}
