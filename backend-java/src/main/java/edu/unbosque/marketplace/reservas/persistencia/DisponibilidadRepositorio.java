package edu.unbosque.marketplace.reservas.persistencia;

import edu.unbosque.marketplace.reservas.domain.Periodo;
import edu.unbosque.marketplace.reservas.domain.RangoFechas;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DisponibilidadRepositorio {

    private final JdbcTemplate jdbc;

    public DisponibilidadRepositorio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Periodos declarados por el anfitrión que se cruzan con [desde, hasta). */
    public List<Periodo> periodos(UUID idAlojamiento, LocalDate desde, LocalDate hasta) {
        return jdbc.query("""
                SELECT fecha_inicio, fecha_fin, tipo FROM reservas.disponibilidad
                 WHERE id_alojamiento = ? AND fecha_fin > ? AND fecha_inicio < ?
                 ORDER BY fecha_inicio""",
                (rs, n) -> new Periodo(rs.getObject("fecha_inicio", LocalDate.class),
                        rs.getObject("fecha_fin", LocalDate.class), Periodo.Tipo.valueOf(rs.getString("tipo"))),
                idAlojamiento, desde, hasta);
    }

    /** Rangos retenidos por reservas CONFIRMADA o PENDIENTE_PAGO vigente (RN-06, RN-10). */
    public List<RangoFechas> reservados(UUID idAlojamiento, LocalDate desde, LocalDate hasta, Instant ahora) {
        return jdbc.query("""
                SELECT fecha_llegada, fecha_salida FROM reservas.reserva
                 WHERE id_alojamiento = ? AND fecha_salida > ? AND fecha_llegada < ?
                   AND (estado = 'CONFIRMADA' OR (estado = 'PENDIENTE_PAGO' AND expira_en > ?))
                 ORDER BY fecha_llegada""",
                (rs, n) -> new RangoFechas(rs.getObject("fecha_llegada", LocalDate.class),
                        rs.getObject("fecha_salida", LocalDate.class)),
                idAlojamiento, desde, hasta, Timestamp.from(ahora));
    }

    /** Reemplaza los periodos declarados del alojamiento (PUT /api/availability/{id}). */
    public void reemplazar(UUID idAlojamiento, List<Periodo> periodos) {
        jdbc.update("DELETE FROM reservas.disponibilidad WHERE id_alojamiento = ?", idAlojamiento);
        for (Periodo p : periodos) {
            jdbc.update("""
                    INSERT INTO reservas.disponibilidad (id, id_alojamiento, fecha_inicio, fecha_fin, tipo)
                    VALUES (?, ?, ?, ?, ?)""", UUID.randomUUID(), idAlojamiento, p.inicio(), p.fin(), p.tipo().name());
        }
    }
}
