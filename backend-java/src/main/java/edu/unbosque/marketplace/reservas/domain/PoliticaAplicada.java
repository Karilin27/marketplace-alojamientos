package edu.unbosque.marketplace.reservas.domain;

import edu.unbosque.marketplace.common.ReglaNegocioException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Política de cancelación vigente al momento de reservar (HU-023 CA-04). La reserva conserva
 * estos valores aunque el anfitrión cambie después la política de la publicación.
 *
 * Regla (página 06, sección 4): con al menos N días de antelación a la llegada se devuelve el
 * porcentaje indicado; en caso contrario, 0 %. No se permite cancelar después de la llegada.
 */
public record PoliticaAplicada(String nombre, int diasAntelacion, int porcentajeDevolucion) {

    /** Políticas del MVP (decisión D-09). */
    public enum Tipo {
        FLEXIBLE(2, 100), MODERADA(7, 100), ESTRICTA(14, 50);

        private final int dias;
        private final int porcentaje;

        Tipo(int dias, int porcentaje) {
            this.dias = dias;
            this.porcentaje = porcentaje;
        }

        public PoliticaAplicada aplicada() {
            return new PoliticaAplicada(name(), dias, porcentaje);
        }
    }

    public BigDecimal calcularDevolucion(BigDecimal totalPagado, LocalDate hoy, LocalDate llegada) {
        long dias = ChronoUnit.DAYS.between(hoy, llegada);
        if (dias < 0) {
            throw ReglaNegocioException.regla("CANCELACION_NO_PERMITIDA",
                    "No se puede cancelar una reserva cuya fecha de llegada ya pasó.");
        }
        if (dias >= diasAntelacion) {
            return totalPagado.multiply(BigDecimal.valueOf(porcentajeDevolucion))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO.setScale(2);
    }
}
