package edu.unbosque.marketplace.reservas.web;

import edu.unbosque.marketplace.reservas.domain.Cotizacion;
import edu.unbosque.marketplace.reservas.domain.EstadoReserva;
import edu.unbosque.marketplace.reservas.domain.Periodo;
import edu.unbosque.marketplace.reservas.domain.PoliticaAplicada;
import edu.unbosque.marketplace.reservas.persistencia.Reserva;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Cuerpos de solicitud y respuesta de S3 (nombres de campo en español, como el resto del contrato). */
public final class Dtos {

    private Dtos() {
    }

    // ---- Disponibilidad (HU-008) ----
    public record PeriodoDto(@NotNull LocalDate fechaInicio, @NotNull LocalDate fechaFin, @NotNull Periodo.Tipo tipo) {
        public static PeriodoDto de(Periodo p) {
            return new PeriodoDto(p.inicio(), p.fin(), p.tipo());
        }
    }

    public record DisponibilidadSolicitud(@NotNull @Valid List<PeriodoDto> periodos) {
    }

    public record DisponibilidadRespuesta(UUID idAlojamiento, List<PeriodoDto> periodos) {
    }

    // ---- Cotización (HU-011) ----
    public record CotizacionSolicitud(
            @NotNull UUID idAlojamiento,
            @NotNull LocalDate fechaLlegada,
            @NotNull LocalDate fechaSalida,
            @NotNull @Min(1) Integer huespedes,
            List<String> idsServiciosAdicionales) {
    }

    public record CotizacionRespuesta(UUID idAlojamiento, LocalDate fechaLlegada, LocalDate fechaSalida,
                                      int huespedes, Cotizacion desglose, PoliticaAplicada politicaAplicada,
                                      String tokenCotizacion, Instant venceEn) {
    }

    // ---- Reservas (HU-012, HU-014, HU-015) ----
    public record ReservaSolicitud(@NotBlank String tokenCotizacion) {
    }

    public record CancelacionSolicitud(String motivo) {
    }

    public record Desglose(BigDecimal subtotal, BigDecimal descuento, BigDecimal tarifaLimpieza,
                           BigDecimal tarifaServicio, List<Cotizacion.Linea> serviciosAdicionales,
                           BigDecimal total) {
    }

    public record ReservaRespuesta(UUID id, UUID idAlojamiento, UUID idHuesped, LocalDate fechaLlegada,
                                   LocalDate fechaSalida, int huespedes, EstadoReserva estado, Desglose desglose,
                                   PoliticaAplicada politicaAplicada, Instant venceEn, boolean revisionManual,
                                   BigDecimal montoReembolso, Instant creadaEn) {
        public static ReservaRespuesta de(Reserva r, List<Cotizacion.Linea> servicios, BigDecimal reembolso) {
            return new ReservaRespuesta(r.id(), r.idAlojamiento(), r.idHuesped(), r.rango().llegada(),
                    r.rango().salida(), r.huespedes(), r.estado(),
                    new Desglose(r.subtotal(), r.descuento(), r.tarifaLimpieza(), r.tarifaServicio(), servicios,
                            r.total()),
                    r.politica(), r.estado() == EstadoReserva.PENDIENTE_PAGO ? r.expiraEn() : null,
                    r.revisionManual(), reembolso, r.creadaEn());
        }
    }

    public record Pagina<T>(List<T> contenido, int page, int size, long total) {
    }
}
