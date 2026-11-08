package edu.unbosque.marketplace.reservas.domain;

import java.time.LocalDate;

/** Periodo del calendario de un alojamiento (HU-008). RESERVADO solo se calcula; no se registra. */
public record Periodo(LocalDate inicio, LocalDate fin, Tipo tipo) {

    public enum Tipo { DISPONIBLE, NO_DISPONIBLE, RESERVADO }

    public RangoFechas rango() {
        return new RangoFechas(inicio, fin);
    }
}
