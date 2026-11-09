package edu.unbosque.marketplace.notificaciones;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class NotificacionRepositorio {

    private static final RowMapper<Notificacion> MAPPER = (rs, n) -> new Notificacion(
            rs.getObject("id", UUID.class), rs.getObject("id_usuario", UUID.class), rs.getString("tipo"),
            rs.getString("mensaje"), rs.getObject("id_reserva", UUID.class), rs.getBoolean("leida"),
            rs.getTimestamp("fecha").toInstant());

    private final JdbcTemplate jdbc;

    public NotificacionRepositorio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insertar(UUID idUsuario, String tipo, String mensaje, UUID idReserva, Instant fecha) {
        jdbc.update("INSERT INTO experiencia.notificacion (id, id_usuario, tipo, mensaje, id_reserva, leida, fecha) "
                        + "VALUES (?,?,?,?,?,false,?)", UUID.randomUUID(), idUsuario, tipo, mensaje, idReserva,
                Timestamp.from(fecha));
    }

    public List<Notificacion> listar(UUID idUsuario, boolean soloNoLeidas, int pagina, int tamano) {
        return jdbc.query("SELECT id, id_usuario, tipo, mensaje, id_reserva, leida, fecha "
                + "FROM experiencia.notificacion WHERE id_usuario = ? AND (NOT ? OR NOT leida) "
                + "ORDER BY fecha DESC LIMIT ? OFFSET ?", MAPPER, idUsuario, soloNoLeidas, tamano,
                (long) pagina * tamano);
    }

    public long contar(UUID idUsuario, boolean soloNoLeidas) {
        return jdbc.queryForObject("SELECT count(*) FROM experiencia.notificacion "
                + "WHERE id_usuario = ? AND (NOT ? OR NOT leida)", Long.class, idUsuario, soloNoLeidas);
    }

    /** Marca como leída solo si pertenece al usuario; devuelve la notificación o vacío. */
    public java.util.Optional<Notificacion> marcarLeida(UUID id, UUID idUsuario) {
        return jdbc.query("UPDATE experiencia.notificacion SET leida = true WHERE id = ? AND id_usuario = ? "
                + "RETURNING id, id_usuario, tipo, mensaje, id_reserva, leida, fecha", MAPPER, id, idUsuario)
                .stream().findFirst();
    }
}
