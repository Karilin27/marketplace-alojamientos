package edu.unbosque.marketplace.seguridad;

import edu.unbosque.marketplace.common.ReglaNegocioException;
import java.util.Arrays;
import java.util.UUID;

/** Usuario autenticado, tomado del JWT que emite S1. */
public record Sesion(UUID id, Rol rol) {

    public enum Rol { HUESPED, ANFITRION, ADMINISTRADOR }

    public Sesion exigirRol(Rol... permitidos) {
        if (Arrays.stream(permitidos).noneMatch(r -> r == rol)) {
            throw ReglaNegocioException.prohibido("Su rol no permite realizar esta operación.");
        }
        return this;
    }

    public boolean esAdmin() {
        return rol == Rol.ADMINISTRADOR;
    }
}
