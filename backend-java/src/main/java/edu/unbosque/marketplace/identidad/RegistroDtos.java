package edu.unbosque.marketplace.identidad;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Cuerpos de solicitud y respuesta de S1 para HU-001 (registro). Nombres de campo en español. */
public final class RegistroDtos {

    private RegistroDtos() {
    }

    /** POST /api/auth/register. rol: HUESPED o ANFITRION. */
    public record RegistroSolicitud(String nombre, String correo, String contrasena, String rol) {
    }

    /** Datos públicos de la cuenta creada; nunca incluye la contraseña ni su hash. */
    public record RegistroRespuesta(UUID id, String nombre, String correo, String rol, String estado,
                                    OffsetDateTime fechaRegistro) {
    }
}
