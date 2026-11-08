package edu.unbosque.marketplace.reservas.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Transiciones de la página 06, sección 3.2 (RN-06, RN-10). */
class EstadoReservaTest {

    @Test
    void transicionesPermitidas() {
        assertTrue(EstadoReserva.PENDIENTE_PAGO.puedePasarA(EstadoReserva.CONFIRMADA));
        assertTrue(EstadoReserva.PENDIENTE_PAGO.puedePasarA(EstadoReserva.RECHAZADA));
        assertTrue(EstadoReserva.CONFIRMADA.puedePasarA(EstadoReserva.CANCELADA));
        assertTrue(EstadoReserva.CONFIRMADA.puedePasarA(EstadoReserva.COMPLETADA));
    }

    @Test
    void transicionesProhibidas() {
        assertFalse(EstadoReserva.PENDIENTE_PAGO.puedePasarA(EstadoReserva.CANCELADA));
        assertFalse(EstadoReserva.PENDIENTE_PAGO.puedePasarA(EstadoReserva.COMPLETADA));
        assertFalse(EstadoReserva.RECHAZADA.puedePasarA(EstadoReserva.CONFIRMADA));
        assertFalse(EstadoReserva.CANCELADA.puedePasarA(EstadoReserva.CONFIRMADA));
        assertFalse(EstadoReserva.COMPLETADA.puedePasarA(EstadoReserva.CANCELADA));
        assertFalse(EstadoReserva.CONFIRMADA.puedePasarA(EstadoReserva.RECHAZADA));
    }

    @Test
    void soloPendienteYConfirmadaRetienenElPeriodo() {
        assertTrue(EstadoReserva.PENDIENTE_PAGO.retieneDisponibilidad());
        assertTrue(EstadoReserva.CONFIRMADA.retieneDisponibilidad());
        assertFalse(EstadoReserva.RECHAZADA.retieneDisponibilidad());
        assertFalse(EstadoReserva.CANCELADA.retieneDisponibilidad());
        assertFalse(EstadoReserva.COMPLETADA.retieneDisponibilidad());
    }
}
