package edu.unbosque.marketplace.reservas.persistencia;

import edu.unbosque.marketplace.reservas.domain.Cotizacion;
import edu.unbosque.marketplace.reservas.domain.EstadoReserva;
import edu.unbosque.marketplace.reservas.domain.PoliticaAplicada;
import edu.unbosque.marketplace.reservas.domain.RangoFechas;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ReservaRepositorio {

    private static final String COLUMNAS = """
            r.id, r.id_alojamiento, r.id_huesped, r.fecha_llegada, r.fecha_salida, r.huespedes, r.estado,
            r.subtotal, r.descuento, r.tarifa_limpieza, r.tarifa_servicio, r.total, r.politica_nombre,
            r.politica_dias, r.politica_pct, r.llave_idempotencia, r.revision_manual, r.expira_en, r.creada_en""";

    private static final RowMapper<Reserva> MAPPER = ReservaRepositorio::mapear;

    private final JdbcTemplate jdbc;

    public ReservaRepositorio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static Reserva mapear(ResultSet rs, int n) throws SQLException {
        return new Reserva(
                rs.getObject("id", UUID.class),
                rs.getObject("id_alojamiento", UUID.class),
                rs.getObject("id_huesped", UUID.class),
                new RangoFechas(rs.getObject("fecha_llegada", LocalDate.class),
                        rs.getObject("fecha_salida", LocalDate.class)),
                rs.getInt("huespedes"),
                EstadoReserva.valueOf(rs.getString("estado")),
                rs.getBigDecimal("subtotal"), rs.getBigDecimal("descuento"), rs.getBigDecimal("tarifa_limpieza"),
                rs.getBigDecimal("tarifa_servicio"), rs.getBigDecimal("total"),
                new PoliticaAplicada(rs.getString("politica_nombre"), rs.getInt("politica_dias"),
                        rs.getInt("politica_pct")),
                rs.getString("llave_idempotencia"),
                rs.getBoolean("revision_manual"),
                rs.getTimestamp("expira_en").toInstant(),
                rs.getTimestamp("creada_en").toInstant());
    }

    /** Inserta la reserva PENDIENTE_PAGO y su desglose de servicios adicionales. */
    public void insertar(Reserva r, List<Cotizacion.Linea> servicios) {
        jdbc.update("""
                INSERT INTO reservas.reserva (id, id_alojamiento, id_huesped, fecha_llegada, fecha_salida, huespedes,
                    estado, subtotal, descuento, tarifa_limpieza, tarifa_servicio, total, politica_nombre,
                    politica_dias, politica_pct, llave_idempotencia, revision_manual, expira_en, creada_en)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                r.id(), r.idAlojamiento(), r.idHuesped(), r.rango().llegada(), r.rango().salida(), r.huespedes(),
                r.estado().name(), r.subtotal(), r.descuento(), r.tarifaLimpieza(), r.tarifaServicio(), r.total(),
                r.politica().nombre(), r.politica().diasAntelacion(), r.politica().porcentajeDevolucion(),
                r.llaveIdempotencia(), r.revisionManual(), Timestamp.from(r.expiraEn()), Timestamp.from(r.creadaEn()));
        for (Cotizacion.Linea l : servicios) {
            jdbc.update("""
                    INSERT INTO reservas.reserva_servicio_adicional (id_reserva, id_servicio, nombre, costo)
                    VALUES (?,?,?,?)""", r.id(), UUID.fromString(l.id()), l.nombre(), l.costo());
        }
        historial(r.id(), null, r.estado(), r.idHuesped(), "Reserva creada");
    }

    public Optional<Reserva> buscar(UUID id) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM reservas.reserva r WHERE r.id = ?", MAPPER, id)
                .stream().findFirst();
    }

    /** Bloquea la fila (SELECT ... FOR UPDATE) dentro de una transacción. */
    public Optional<Reserva> bloquear(UUID id) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM reservas.reserva r WHERE r.id = ? FOR UPDATE", MAPPER, id)
                .stream().findFirst();
    }

    public Optional<Reserva> buscarPorLlave(UUID idHuesped, String llave) {
        return jdbc.query("SELECT " + COLUMNAS
                + " FROM reservas.reserva r WHERE r.id_huesped = ? AND r.llave_idempotencia = ?", MAPPER,
                idHuesped, llave).stream().findFirst();
    }

    public List<Cotizacion.Linea> servicios(UUID idReserva) {
        return jdbc.query("SELECT id_servicio, nombre, costo FROM reservas.reserva_servicio_adicional "
                        + "WHERE id_reserva = ? ORDER BY nombre",
                (rs, n) -> new Cotizacion.Linea(rs.getObject("id_servicio", UUID.class).toString(),
                        rs.getString("nombre"), rs.getBigDecimal("costo")), idReserva);
    }

    public List<Reserva> listarPorHuesped(UUID idHuesped, int pagina, int tamano) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM reservas.reserva r WHERE r.id_huesped = ? "
                + "ORDER BY r.creada_en DESC LIMIT ? OFFSET ?", MAPPER, idHuesped, tamano, (long) pagina * tamano);
    }

    public long contarPorHuesped(UUID idHuesped) {
        return jdbc.queryForObject("SELECT count(*) FROM reservas.reserva WHERE id_huesped = ?", Long.class,
                idHuesped);
    }

    public List<Reserva> listarPorAnfitrion(UUID idAnfitrion, int pagina, int tamano) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM reservas.reserva r "
                + "JOIN reservas.v_alojamiento a ON a.id = r.id_alojamiento WHERE a.id_anfitrion = ? "
                + "ORDER BY r.creada_en DESC LIMIT ? OFFSET ?", MAPPER, idAnfitrion, tamano, (long) pagina * tamano);
    }

    public long contarPorAnfitrion(UUID idAnfitrion) {
        return jdbc.queryForObject("SELECT count(*) FROM reservas.reserva r "
                + "JOIN reservas.v_alojamiento a ON a.id = r.id_alojamiento WHERE a.id_anfitrion = ?", Long.class,
                idAnfitrion);
    }

    /** Cambia el estado solo si sigue en el esperado (control de concurrencia optimista). */
    public boolean cambiarEstado(UUID id, EstadoReserva esperado, EstadoReserva nuevo, UUID actor, String motivo) {
        int filas = jdbc.update("UPDATE reservas.reserva SET estado = ?, actualizada_en = now() "
                + "WHERE id = ? AND estado = ?", nuevo.name(), id, esperado.name());
        if (filas == 1) {
            historial(id, esperado, nuevo, actor, motivo);
        }
        return filas == 1;
    }

    public void marcarRevisionManual(UUID id) {
        jdbc.update("UPDATE reservas.reserva SET revision_manual = true, actualizada_en = now() WHERE id = ?", id);
    }

    public void insertarCancelacion(UUID idReserva, UUID canceladoPor, String motivo, BigDecimal montoReembolso,
                                    String llave, Instant fecha) {
        jdbc.update("""
                INSERT INTO reservas.cancelacion (id, id_reserva, id_cancelado_por, motivo, monto_reembolso,
                    llave_idempotencia, fecha) VALUES (?,?,?,?,?,?,?)""", UUID.randomUUID(), idReserva, canceladoPor,
                motivo, montoReembolso, llave, Timestamp.from(fecha));
    }

    public Optional<UUID> reservaDeCancelacionPorLlave(UUID idUsuario, String llave) {
        return jdbc.query("SELECT id_reserva FROM reservas.cancelacion WHERE id_cancelado_por = ? "
                        + "AND llave_idempotencia = ?", (rs, n) -> rs.getObject("id_reserva", UUID.class),
                idUsuario, llave).stream().findFirst();
    }

    public Optional<BigDecimal> montoReembolso(UUID idReserva) {
        return jdbc.query("SELECT monto_reembolso FROM reservas.cancelacion WHERE id_reserva = ?",
                (rs, n) -> rs.getBigDecimal("monto_reembolso"), idReserva).stream().findFirst();
    }

    /** PENDIENTE_PAGO vencidas -> RECHAZADA (RN-10). Devuelve las reservas afectadas. */
    public List<Reserva> expirarVencidas(Instant ahora) {
        List<Reserva> vencidas = jdbc.query("SELECT " + COLUMNAS + " FROM reservas.reserva r "
                + "WHERE r.estado = 'PENDIENTE_PAGO' AND r.expira_en <= ? FOR UPDATE SKIP LOCKED", MAPPER,
                Timestamp.from(ahora));
        return vencidas.stream()
                .filter(r -> cambiarEstado(r.id(), EstadoReserva.PENDIENTE_PAGO, EstadoReserva.RECHAZADA, null,
                        "Venció la retención de pago"))
                .toList();
    }

    /** CONFIRMADA con salida anterior a hoy -> COMPLETADA. */
    public List<Reserva> completarFinalizadas(LocalDate hoy) {
        List<Reserva> finalizadas = jdbc.query("SELECT " + COLUMNAS + " FROM reservas.reserva r "
                + "WHERE r.estado = 'CONFIRMADA' AND r.fecha_salida < ? FOR UPDATE SKIP LOCKED", MAPPER, hoy);
        return finalizadas.stream()
                .filter(r -> cambiarEstado(r.id(), EstadoReserva.CONFIRMADA, EstadoReserva.COMPLETADA, null,
                        "Estancia finalizada"))
                .toList();
    }

    private void historial(UUID idReserva, EstadoReserva anterior, EstadoReserva nuevo, UUID actor, String motivo) {
        jdbc.update("""
                INSERT INTO reservas.reserva_historial (id, id_reserva, estado_anterior, estado_nuevo, id_actor, motivo)
                VALUES (?,?,?,?,?,?)""", UUID.randomUUID(), idReserva, anterior == null ? null : anterior.name(),
                nuevo.name(), actor, motivo);
    }
}
