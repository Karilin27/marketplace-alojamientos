package edu.unbosque.marketplace.reservas.domain;

import java.math.BigDecimal;
import java.util.List;

/** Desglose de una cotización (RN-07). Todos los montos tienen dos decimales. */
public record Cotizacion(
        long noches,
        BigDecimal precioPorNoche,
        BigDecimal subtotal,
        BigDecimal descuento,
        BigDecimal tarifaLimpieza,
        BigDecimal tarifaServicio,
        List<Linea> serviciosAdicionales,
        BigDecimal total) {

    public record Linea(String id, String nombre, BigDecimal costo) {
    }
}
