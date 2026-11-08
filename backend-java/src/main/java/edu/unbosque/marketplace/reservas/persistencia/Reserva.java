package edu.unbosque.marketplace.reservas.persistencia;

import edu.unbosque.marketplace.reservas.domain.EstadoReserva;
import edu.unbosque.marketplace.reservas.domain.PoliticaAplicada;
import edu.unbosque.marketplace.reservas.domain.RangoFechas;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Fila de reservas.reserva. */
public record Reserva(
        UUID id,
        UUID idAlojamiento,
        UUID idHuesped,
        RangoFechas rango,
        int huespedes,
        EstadoReserva estado,
        BigDecimal subtotal,
        BigDecimal descuento,
        BigDecimal tarifaLimpieza,
        BigDecimal tarifaServicio,
        BigDecimal total,
        PoliticaAplicada politica,
        String llaveIdempotencia,
        boolean revisionManual,
        Instant expiraEn,
        Instant creadaEn) {
}
