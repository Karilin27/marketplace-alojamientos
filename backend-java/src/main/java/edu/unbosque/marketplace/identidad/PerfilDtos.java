package edu.unbosque.marketplace.identidad;

import java.util.UUID;

/** Cuerpos de solicitud y respuesta de S1 para HU-004 (perfil y cuenta). Nombres de campo en español. */
public final class PerfilDtos {

    private PerfilDtos() {
    }

    /** PUT /api/users/me. El correo y el rol no se modifican desde aquí. */
    public record PerfilSolicitud(String nombre, String telefono, String documento, String pais, String fotoUrl) {
    }

    /** PATCH /api/users/me/status. estado: ACTIVO o DESACTIVADO. */
    public record EstadoSolicitud(String estado) {
    }

    public record PerfilRespuesta(UUID id, String nombre, String correo, String telefono, String documento,
                                  String pais, String fotoUrl, String rol, String estado) {
    }

    /** sesionCerrada = true cuando la cuenta quedó desactivada: el cliente debe descartar su token (CA-05). */
    public record EstadoRespuesta(String estado, boolean sesionCerrada, int publicacionesPausadas) {
    }
}
