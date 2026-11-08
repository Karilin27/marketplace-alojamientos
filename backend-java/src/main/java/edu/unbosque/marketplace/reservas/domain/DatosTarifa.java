package edu.unbosque.marketplace.reservas.domain;

import java.math.BigDecimal;

/**
 * Datos de S2 que S3 necesita para cotizar (página 10, sección 7).
 * Los porcentajes se expresan de 0 a 100 (por ejemplo 10 = 10 %).
 */
public record DatosTarifa(
        BigDecimal precioPorNoche,
        BigDecimal tarifaLimpieza,
        BigDecimal descuentoPct,
        int descuentoMinNoches,
        BigDecimal tarifaServicioPct,
        int capacidad) {
}
