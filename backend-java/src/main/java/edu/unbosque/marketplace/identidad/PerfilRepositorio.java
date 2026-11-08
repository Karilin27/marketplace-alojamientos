package edu.unbosque.marketplace.identidad;

import edu.unbosque.marketplace.identidad.ReglasPerfil.Datos;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Acceso a datos de S1 para HU-004: esquema identidad (y lectura/pausa controlada de catalogo y reservas). */
@Repository
public class PerfilRepositorio {

    public record Cuenta(UUID id, String nombre, String correo, String rol, String estado, String telefono,
                         String documento, String pais, String fotoUrl) {
    }

    private final JdbcTemplate jdbc;

    public PerfilRepositorio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Cuenta> buscar(UUID id) {
        return consultar(id, "");
    }

    /** Bloquea la fila del usuario hasta el fin de la transacción (evita dos cambios de estado a la vez). */
    public Optional<Cuenta> buscarBloqueando(UUID id) {
        return consultar(id, " FOR UPDATE OF u");
    }

    private Optional<Cuenta> consultar(UUID id, String sufijo) {
        return jdbc.query("""
                SELECT u.id, u.nombre, u.correo, u.rol, u.estado, p.telefono, p.documento, p.pais, p.foto_url
                  FROM identidad.usuario u LEFT JOIN identidad.perfil p ON p.id_usuario = u.id
                 WHERE u.id = ?""" + sufijo, (rs, n) -> new Cuenta(
                rs.getObject("id", UUID.class), rs.getString("nombre"), rs.getString("correo"),
                rs.getString("rol"), rs.getString("estado"), rs.getString("telefono"),
                rs.getString("documento"), rs.getString("pais"), rs.getString("foto_url")), id)
                .stream().findFirst();
    }

    public void actualizar(UUID id, Datos d) {
        jdbc.update("UPDATE identidad.usuario SET nombre = ? WHERE id = ?", d.nombre(), id);
        jdbc.update("""
                INSERT INTO identidad.perfil (id_usuario, telefono, documento, pais, foto_url)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (id_usuario) DO UPDATE
                   SET telefono = EXCLUDED.telefono, documento = EXCLUDED.documento,
                       pais = EXCLUDED.pais, foto_url = EXCLUDED.foto_url""",
                id, d.telefono(), d.documento(), d.pais(), d.fotoUrl());
    }

    /**
     * Reservas CONFIRMADAS cuya estadía aún no termina, ya sea como huésped o como anfitrión (CA-05, CA-06).
     * Se considera "futura" una reserva con fecha de salida posterior a hoy.
     */
    public long reservasConfirmadasFuturas(UUID idUsuario, LocalDate hoy) {
        Long n = jdbc.queryForObject("""
                SELECT count(*) FROM reservas.reserva r
                 WHERE r.estado = 'CONFIRMADA' AND r.fecha_salida > ?
                   AND (r.id_huesped = ?
                        OR r.id_alojamiento IN (SELECT a.id FROM catalogo.alojamiento a WHERE a.id_anfitrion = ?))""",
                Long.class, hoy, idUsuario, idUsuario);
        return n == null ? 0 : n;
    }

    public void cambiarEstado(UUID id, String estado) {
        jdbc.update("UPDATE identidad.usuario SET estado = ? WHERE id = ?", estado, id);
    }

    /** CA-05: las publicaciones ACTIVAS del anfitrión pasan a PAUSADA. Devuelve cuántas se pausaron. */
    public int pausarPublicacionesActivas(UUID idAnfitrion) {
        return jdbc.update("""
                UPDATE catalogo.alojamiento SET estado_publicacion = 'PAUSADA'
                 WHERE id_anfitrion = ? AND estado_publicacion = 'ACTIVA'""", idAnfitrion);
    }
}
