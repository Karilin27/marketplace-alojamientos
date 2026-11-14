package edu.unbosque.marketplace.administracion;

import edu.unbosque.marketplace.logs.AuditoriaPuerto;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * HU-019 / RN-14 — Implementa el puerto de auditoría: cada evento que EventLogger.auditado(...) publica queda en
 * administracion.auditoria (usuario, entidad, identificador, acción, resultado y fecha), en solo lectura.
 */
@Component
public class AuditoriaJdbc implements AuditoriaPuerto {

    private final JdbcTemplate jdbc;

    public AuditoriaJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void registrar(String usuario, String entidad, String idEntidad, String accion, String resultado) {
        jdbc.update("""
                INSERT INTO administracion.auditoria (id_usuario, entidad, id_entidad, accion, resultado)
                VALUES (?, ?, ?, ?, ?)""", uuid(usuario), entidad == null ? "desconocida" : entidad, idEntidad,
                accion, resultado == null ? "DESCONOCIDO" : resultado);
    }

    static UUID uuid(String valor) {
        if (valor == null) return null;
        try {
            return UUID.fromString(valor);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
