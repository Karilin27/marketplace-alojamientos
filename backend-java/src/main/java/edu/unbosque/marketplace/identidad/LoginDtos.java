package edu.unbosque.marketplace.identidad;

import java.time.Instant;
import java.util.UUID;

/** Cuerpos de solicitud y respuesta de S1 para HU-002 (inicio de sesión). Nombres de campo en español. */
public final class LoginDtos {

    private LoginDtos() {
    }

    /**
     * POST /api/auth/login. reactivar = true solo cuando una cuenta DESACTIVADO pide volver a ACTIVO
     * con sus credenciales (HU-004 CA-07).
     */
    public record LoginSolicitud(String correo, String contrasena, Boolean reactivar) {
    }

    /** Datos públicos del usuario autenticado; nunca incluye la contraseña ni su hash. */
    public record UsuarioSesion(UUID id, String nombre, String correo, String rol, String estado) {
    }

    /** token: JWT para la cabecera "Authorization: Bearer". */
    public record LoginRespuesta(String token, String tipo, Instant expiraEn, UsuarioSesion usuario) {
    }
}
