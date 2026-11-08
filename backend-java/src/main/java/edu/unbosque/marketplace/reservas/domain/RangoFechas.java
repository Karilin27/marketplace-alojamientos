package edu.unbosque.marketplace.reservas.domain;

import edu.unbosque.marketplace.common.ReglaNegocioException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Objects;

/** Rango de fechas [llegada, salida): la noche de salida queda libre (RN-04, RN-06). */
public record RangoFechas(LocalDate llegada, LocalDate salida) {

    public RangoFechas {
        Objects.requireNonNull(llegada, "llegada");
        Objects.requireNonNull(salida, "salida");
    }

    /** Valida RN-04 y que la llegada no esté en el pasado. */
    public static RangoFechas validado(LocalDate llegada, LocalDate salida, LocalDate hoy) {
        if (llegada == null || salida == null) {
            throw ReglaNegocioException.solicitudInvalida("FECHAS_REQUERIDAS",
                    "Debe indicar la fecha de llegada y la de salida.");
        }
        if (!salida.isAfter(llegada)) {
            throw ReglaNegocioException.regla("FECHAS_INVALIDAS",
                    "La fecha de salida debe ser posterior a la fecha de llegada.");
        }
        if (llegada.isBefore(hoy)) {
            throw ReglaNegocioException.regla("FECHAS_INVALIDAS",
                    "La fecha de llegada no puede estar en el pasado.");
        }
        return new RangoFechas(llegada, salida);
    }

    public long noches() {
        return ChronoUnit.DAYS.between(llegada, salida);
    }

    public boolean solapaCon(RangoFechas otro) {
        return llegada.isBefore(otro.salida) && otro.llegada.isBefore(salida);
    }

    public boolean solapaConAlguno(Collection<RangoFechas> otros) {
        return otros.stream().anyMatch(this::solapaCon);
    }
}
