package edu.unbosque.marketplace.reservas.domain;

import edu.unbosque.marketplace.common.ReglaNegocioException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/** Reglas de disponibilidad (RN-04, RN-06; HU-008 CA-03 y CA-04). */
public final class ReglasDisponibilidad {

    private ReglasDisponibilidad() {
    }

    /**
     * Valida los periodos que registra el anfitrión: fechas coherentes, no en el pasado,
     * tipo DISPONIBLE o NO_DISPONIBLE y sin superposición entre sí. Devuelve la lista ordenada.
     */
    public static List<Periodo> validar(List<Periodo> periodos, LocalDate hoy) {
        List<Periodo> ordenados = new ArrayList<>(periodos);
        for (Periodo p : ordenados) {
            if (p.inicio() == null || p.fin() == null || p.tipo() == null) {
                throw ReglaNegocioException.solicitudInvalida("PERIODO_INCOMPLETO",
                        "Cada periodo requiere fecha de inicio, fecha de fin y tipo.");
            }
            if (p.tipo() == Periodo.Tipo.RESERVADO) {
                throw ReglaNegocioException.regla("TIPO_PERIODO_INVALIDO",
                        "El tipo RESERVADO lo asigna el sistema; use DISPONIBLE o NO_DISPONIBLE.");
            }
            if (!p.fin().isAfter(p.inicio())) {
                throw ReglaNegocioException.regla("FECHAS_INVALIDAS",
                        "La fecha de fin debe ser posterior a la de inicio.");
            }
            if (p.inicio().isBefore(hoy)) {
                throw ReglaNegocioException.regla("PERIODO_EN_EL_PASADO",
                        "No se pueden registrar periodos en el pasado.");
            }
        }
        ordenados.sort(Comparator.comparing(Periodo::inicio));
        for (int i = 1; i < ordenados.size(); i++) {
            if (ordenados.get(i - 1).rango().solapaCon(ordenados.get(i).rango())) {
                throw ReglaNegocioException.regla("PERIODOS_SUPERPUESTOS",
                        "Los periodos registrados no pueden superponerse entre sí.");
            }
        }
        return ordenados;
    }

    /** Reservas que impiden marcar el periodo como no disponible (HU-008 CA-04). */
    public static List<RangoFechas> choques(RangoFechas periodo, Collection<RangoFechas> reservas) {
        return reservas.stream().filter(periodo::solapaCon).toList();
    }

    /** Un rango está libre si no choca con periodos NO_DISPONIBLE ni con reservas que retienen el periodo. */
    public static boolean libre(RangoFechas rango, Collection<Periodo> periodos,
                                Collection<RangoFechas> reservasActivas) {
        boolean bloqueado = periodos.stream()
                .anyMatch(p -> p.tipo() == Periodo.Tipo.NO_DISPONIBLE && rango.solapaCon(p.rango()));
        return !bloqueado && !rango.solapaConAlguno(reservasActivas);
    }
}
