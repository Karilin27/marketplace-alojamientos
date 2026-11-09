package edu.unbosque.marketplace.identidad;

import edu.unbosque.marketplace.identidad.RegistroDtos.RegistroRespuesta;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Acceso a identidad.usuario para S1 (registro e inicio de sesión). */
@Repository
public class UsuarioRepositorio {

    private final JdbcTemplate jdbc;

    public UsuarioRepositorio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** El índice único usuario_correo_uk es sobre lower(correo): se compara igual aquí. */
    public boolean existeCorreo(String correo) {
        Boolean existe = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM identidad.usuario WHERE lower(correo) = lower(?))",
                Boolean.class, correo);
        return Boolean.TRUE.equals(existe);
    }

    /** id, estado y fecha_registro los asigna la base (DEFAULT gen_random_uuid(), 'ACTIVO', now()). */
    public RegistroRespuesta insertar(String nombre, String correo, String contrasenaHash, String rol) {
        return jdbc.queryForObject("""
                INSERT INTO identidad.usuario (nombre, correo, contrasena_hash, rol)
                VALUES (?, ?, ?, ?)
                RETURNING id, nombre, correo, rol, estado, fecha_registro""",
                (rs, n) -> new RegistroRespuesta(
                        rs.getObject("id", UUID.class), rs.getString("nombre"), rs.getString("correo"),
                        rs.getString("rol"), rs.getString("estado"),
                        rs.getObject("fecha_registro", OffsetDateTime.class)),
                nombre, correo, contrasenaHash, rol);
    }
}
