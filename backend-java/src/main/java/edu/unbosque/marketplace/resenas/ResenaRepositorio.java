package edu.unbosque.marketplace.resenas;

import edu.unbosque.marketplace.resenas.ResenaDtos.ResenaAdmin;
import edu.unbosque.marketplace.resenas.ResenaDtos.ResenaPublica;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** S5 — experiencia.resena y su moderación (administracion.moderacion). Lee reservas.reserva solo para validar. */
@Repository
public class ResenaRepositorio {

    /** Datos de la reserva que se quiere reseñar. */
    public record ReservaResenable(UUID id, UUID idHuesped, UUID idAlojamiento, String estado) {
    }

    public record Resena(UUID id, UUID idAlojamiento, boolean visible) {
    }

    private final JdbcTemplate jdbc;

    public ResenaRepositorio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ReservaResenable> reserva(UUID idReserva) {
        return jdbc.query("SELECT id, id_huesped, id_alojamiento, estado FROM reservas.reserva WHERE id = ?",
                (rs, n) -> new ReservaResenable(rs.getObject("id", UUID.class), rs.getObject("id_huesped", UUID.class),
                        rs.getObject("id_alojamiento", UUID.class), rs.getString("estado")), idReserva)
                .stream().findFirst();
    }

    public boolean tieneResena(UUID idReserva) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM experiencia.resena WHERE id_reserva = ?", Long.class,
                idReserva);
        return n != null && n > 0;
    }

    /** CA-04: huésped, reserva, alojamiento, fecha, calificación y contenido. */
    public UUID insertar(UUID idReserva, UUID idAlojamiento, UUID idUsuario, int calificacion, String comentario) {
        return jdbc.queryForObject("""
                INSERT INTO experiencia.resena (id_reserva, id_alojamiento, id_usuario, calificacion, comentario)
                VALUES (?, ?, ?, ?, ?) RETURNING id""", UUID.class,
                idReserva, idAlojamiento, idUsuario, calificacion, comentario);
    }

    /** CA-06: solo las visibles (RN-20), de la más reciente a la más antigua. */
    public List<ResenaPublica> visibles(UUID idAlojamiento) {
        return jdbc.query("""
                SELECT r.id, r.calificacion, r.comentario, u.nombre, r.fecha
                  FROM experiencia.resena r JOIN identidad.usuario u ON u.id = r.id_usuario
                 WHERE r.id_alojamiento = ? AND r.visible
                 ORDER BY r.fecha DESC""",
                (rs, n) -> new ResenaPublica(rs.getObject("id", UUID.class), rs.getInt("calificacion"),
                        rs.getString("comentario"), rs.getString("nombre"), instante(rs.getTimestamp("fecha"))),
                idAlojamiento);
    }

    /** HU-025 CA-02: el promedio solo cuenta las visibles. */
    public BigDecimal promedio(UUID idAlojamiento) {
        BigDecimal p = jdbc.queryForObject(
                "SELECT avg(calificacion)::numeric FROM experiencia.resena WHERE id_alojamiento = ? AND visible",
                BigDecimal.class, idAlojamiento);
        return p == null ? null : p.setScale(1, RoundingMode.HALF_UP);
    }

    public boolean existeAlojamiento(UUID idAlojamiento) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM catalogo.alojamiento WHERE id = ?", Long.class,
                idAlojamiento);
        return n != null && n > 0;
    }

    /** Vista del administrador: todas, incluidas las ocultas; opcionalmente de un alojamiento. */
    public List<ResenaAdmin> paraAdministrador(UUID idAlojamiento, int limite) {
        List<Object> p = new ArrayList<>();
        String filtro = "";
        if (idAlojamiento != null) {
            filtro = " WHERE r.id_alojamiento = ?";
            p.add(idAlojamiento);
        }
        p.add(limite);
        return jdbc.query("""
                SELECT r.id, r.id_alojamiento, a.titulo, r.id_reserva, r.id_usuario, u.nombre, r.calificacion,
                       r.comentario, r.visible, r.motivo_oculta, r.fecha
                  FROM experiencia.resena r
                  JOIN identidad.usuario u ON u.id = r.id_usuario
                  JOIN catalogo.alojamiento a ON a.id = r.id_alojamiento""" + filtro + " ORDER BY r.fecha DESC LIMIT ?",
                (rs, n) -> new ResenaAdmin(rs.getObject("id", UUID.class), rs.getObject("id_alojamiento", UUID.class),
                        rs.getString("titulo"), rs.getObject("id_reserva", UUID.class),
                        rs.getObject("id_usuario", UUID.class), rs.getString("nombre"), rs.getInt("calificacion"),
                        rs.getString("comentario"), rs.getBoolean("visible"), rs.getString("motivo_oculta"),
                        instante(rs.getTimestamp("fecha"))), p.toArray());
    }

    public Optional<Resena> buscarBloqueando(UUID id) {
        return jdbc.query("SELECT id, id_alojamiento, visible FROM experiencia.resena WHERE id = ? FOR UPDATE",
                (rs, n) -> new Resena(rs.getObject("id", UUID.class), rs.getObject("id_alojamiento", UUID.class),
                        rs.getBoolean("visible")), id).stream().findFirst();
    }

    /** RN-20: la reseña oculta se conserva; al restablecerla se borra el motivo. */
    public void cambiarVisibilidad(UUID id, boolean visible, String motivo) {
        jdbc.update("UPDATE experiencia.resena SET visible = ?, motivo_oculta = ? WHERE id = ?",
                visible, visible ? null : motivo, id);
    }

    /** HU-025 CA-05: quién, qué reseña, acción, motivo y fecha. */
    public Instant registrarModeracion(UUID idAdmin, UUID idResena, UUID idAlojamiento, String accion, String motivo) {
        Timestamp t = jdbc.queryForObject("""
                INSERT INTO administracion.moderacion (id_admin, id_alojamiento, id_resena, accion, motivo)
                VALUES (?, ?, ?, ?, ?) RETURNING fecha""", Timestamp.class,
                idAdmin, idAlojamiento, idResena, accion, motivo);
        return instante(t);
    }

    private static Instant instante(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
