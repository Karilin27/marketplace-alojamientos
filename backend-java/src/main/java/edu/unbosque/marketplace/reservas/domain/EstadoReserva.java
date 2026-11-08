package edu.unbosque.marketplace.reservas.domain;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

/** Estados y transiciones de la reserva (página 06, sección 3.2). */
public enum EstadoReserva {
    PENDIENTE_PAGO, CONFIRMADA, RECHAZADA, CANCELADA, COMPLETADA;

    public Set<EstadoReserva> siguientes() {
        return switch (this) {
            case PENDIENTE_PAGO -> EnumSet.of(CONFIRMADA, RECHAZADA);
            case CONFIRMADA -> EnumSet.of(CANCELADA, COMPLETADA);
            default -> EnumSet.noneOf(EstadoReserva.class);
        };
    }

    public boolean puedePasarA(EstadoReserva destino) {
        return siguientes().contains(destino);
    }

    /** Solo estos estados ocupan el periodo (restricción de exclusión, página 12). */
    public boolean retieneDisponibilidad() {
        return this == PENDIENTE_PAGO || this == CONFIRMADA;
    }

    /** HU-015 CA-04: solo se cancela una reserva CONFIRMADA cuya llegada no ha pasado. */
    public boolean puedeCancelarse(LocalDate llegada, LocalDate hoy) {
        return this == CONFIRMADA && !llegada.isBefore(hoy);
    }
}
