package edu.unbosque.marketplace.notificaciones;

import java.time.Instant;
import java.util.UUID;

public record Notificacion(UUID id, UUID idUsuario, String tipo, String mensaje, UUID idReserva, boolean leida,
                           Instant fecha) {
}
