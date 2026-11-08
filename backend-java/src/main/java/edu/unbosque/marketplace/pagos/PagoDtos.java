package edu.unbosque.marketplace.pagos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class PagoDtos {

    private PagoDtos() {
    }

    /** metodoPago: identificador de prueba (p. ej. pm_card_visa). Nunca se reciben números de tarjeta. */
    public record PagoSolicitud(@NotNull UUID idReserva, @NotBlank String metodoPago) {
    }

    public record PagoRespuesta(UUID id, UUID idReserva, String proveedor, BigDecimal monto, String estado,
                                String idOperacionExterna, String estadoReserva, Instant fecha) {
    }
}
