package edu.unbosque.marketplace.pagos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class PagoRepositorio {

    public record Pago(UUID id, UUID idReserva, UUID idHuesped, String proveedor, String idOperacionExterna,
                       BigDecimal monto, String estado, String motivoRechazo, BigDecimal montoReembolsado,
                       Instant fecha) {
    }

    private static final String COLUMNAS = "id, id_reserva, id_huesped, proveedor, id_operacion_externa, monto, "
            + "estado, motivo_rechazo, monto_reembolsado, fecha";

    private static final RowMapper<Pago> MAPPER = (rs, n) -> new Pago(rs.getObject("id", UUID.class),
            rs.getObject("id_reserva", UUID.class), rs.getObject("id_huesped", UUID.class), rs.getString("proveedor"),
            rs.getString("id_operacion_externa"), rs.getBigDecimal("monto"), rs.getString("estado"),
            rs.getString("motivo_rechazo"), rs.getBigDecimal("monto_reembolsado"), rs.getTimestamp("fecha").toInstant());

    private final JdbcTemplate jdbc;

    public PagoRepositorio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insertarPendiente(UUID id, UUID idReserva, UUID idHuesped, String proveedor, BigDecimal monto,
                                  String llave) {
        jdbc.update("INSERT INTO pagos.pago (id, id_reserva, id_huesped, proveedor, monto, estado, "
                + "llave_idempotencia, fecha) VALUES (?,?,?,?,?,'PENDIENTE',?, now())",
                id, idReserva, idHuesped, proveedor, monto, llave);
    }

    public Optional<Pago> buscarPorLlave(UUID idHuesped, String llave) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM pagos.pago WHERE id_huesped = ? AND llave_idempotencia = ?",
                MAPPER, idHuesped, llave).stream().findFirst();
    }

    public Optional<Pago> buscar(UUID id) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM pagos.pago WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public Optional<Pago> aprobadoDeReserva(UUID idReserva) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM pagos.pago WHERE id_reserva = ? "
                + "AND estado IN ('APROBADO','REEMBOLSO_PARCIAL')", MAPPER, idReserva).stream().findFirst();
    }

    public void resolver(UUID id, String estado, String idOperacionExterna, String motivoRechazo) {
        jdbc.update("UPDATE pagos.pago SET estado = ?, id_operacion_externa = COALESCE(?, id_operacion_externa), "
                + "motivo_rechazo = ? WHERE id = ?", estado, idOperacionExterna, motivoRechazo, id);
    }

    public void registrarReembolso(UUID id, String estado, BigDecimal monto) {
        jdbc.update("UPDATE pagos.pago SET estado = ?, monto_reembolsado = ? WHERE id = ?", estado, monto, id);
    }
}
