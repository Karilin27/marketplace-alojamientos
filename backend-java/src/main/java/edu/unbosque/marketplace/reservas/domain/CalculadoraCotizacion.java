package edu.unbosque.marketplace.reservas.domain;

import edu.unbosque.marketplace.common.ReglaNegocioException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Fórmula de la cotización (página 06, sección 5; RN-07 y RN-17):
 * <pre>
 * subtotal           = noches × precio_por_noche
 * descuento          = subtotal × %descuento   (solo si noches ≥ mínimo de noches)
 * tarifa_de_servicio = (subtotal − descuento) × %tarifa_servicio
 * total              = subtotal − descuento + limpieza + Σ servicios_adicionales + tarifa_de_servicio
 * </pre>
 */
public final class CalculadoraCotizacion {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private CalculadoraCotizacion() {
    }

    public static Cotizacion calcular(DatosTarifa tarifa, RangoFechas rango, int huespedes,
                                      List<ServicioAdicional> servicios) {
        if (huespedes < 1) {
            throw ReglaNegocioException.solicitudInvalida("HUESPEDES_INVALIDOS",
                    "El número de huéspedes debe ser al menos 1.");
        }
        if (huespedes > tarifa.capacidad()) {
            throw ReglaNegocioException.regla("CAPACIDAD_EXCEDIDA",
                    "Se excede la capacidad máxima del alojamiento (" + tarifa.capacidad() + " huéspedes).");
        }
        long noches = rango.noches();
        BigDecimal subtotal = dinero(tarifa.precioPorNoche().multiply(BigDecimal.valueOf(noches)));

        BigDecimal descuento = dinero(BigDecimal.ZERO);
        if (tarifa.descuentoPct() != null && tarifa.descuentoPct().signum() > 0
                && noches >= tarifa.descuentoMinNoches()) {
            descuento = porcentaje(subtotal, tarifa.descuentoPct());
        }
        BigDecimal baseServicio = subtotal.subtract(descuento);
        BigDecimal tarifaServicio = porcentaje(baseServicio, tarifa.tarifaServicioPct());
        BigDecimal limpieza = dinero(tarifa.tarifaLimpieza());

        List<Cotizacion.Linea> lineas = servicios.stream()
                .map(s -> new Cotizacion.Linea(s.id(), s.nombre(), dinero(s.costo())))
                .toList();
        BigDecimal adicionales = lineas.stream().map(Cotizacion.Linea::costo)
                .reduce(dinero(BigDecimal.ZERO), BigDecimal::add);

        BigDecimal total = baseServicio.add(limpieza).add(adicionales).add(tarifaServicio);
        return new Cotizacion(noches, dinero(tarifa.precioPorNoche()), subtotal, descuento, limpieza,
                tarifaServicio, lineas, dinero(total));
    }

    static BigDecimal porcentaje(BigDecimal base, BigDecimal pct) {
        return dinero(base.multiply(pct).divide(CIEN, 6, RoundingMode.HALF_UP));
    }

    static BigDecimal dinero(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
