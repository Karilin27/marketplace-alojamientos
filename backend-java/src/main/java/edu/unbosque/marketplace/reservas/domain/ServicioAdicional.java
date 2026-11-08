package edu.unbosque.marketplace.reservas.domain;

import java.math.BigDecimal;

/** Servicio con costo que el anfitrión ofrece (HU-024), por ejemplo "Desayuno". */
public record ServicioAdicional(String id, String nombre, BigDecimal costo) {
}
