package edu.unbosque.marketplace.alojamientos;

import edu.unbosque.marketplace.alojamientos.AlojamientoDtos.AlojamientoRespuesta;
import edu.unbosque.marketplace.alojamientos.AlojamientoDtos.PaginaResultados;
import edu.unbosque.marketplace.alojamientos.AlojamientoRepositorio.Filtros;
import edu.unbosque.marketplace.alojamientos.AlojamientoRepositorio.Registro;
import edu.unbosque.marketplace.common.ReglaNegocioException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** HU-009 (buscar alojamientos) y HU-010 (consultar detalle). Ambas son públicas. */
@Service
public class BusquedaService {

    public static final int TAMANO_MAXIMO = 50;

    private final AlojamientoRepositorio repo;
    private final AlojamientoConsultas consultas;
    private final Clock reloj;

    public BusquedaService(AlojamientoRepositorio repo, AlojamientoConsultas consultas, Clock reloj) {
        this.repo = repo;
        this.consultas = consultas;
        this.reloj = reloj;
    }

    // ------------------------------------------------------------------ HU-009
    public PaginaResultados buscar(String ciudad, LocalDate llegada, LocalDate salida, Integer huespedes, String tipo,
                                   BigDecimal precioMin, BigDecimal precioMax, List<String> servicios,
                                   int page, int size) {
        Filtros f = new Filtros(ReglasAlojamiento.texto(ciudad), llegada, salida, huespedes,
                ReglasAlojamiento.sinTildes(ReglasAlojamiento.texto(tipo)), precioMin, precioMax,
                ReglasAlojamiento.sinRepetidos(servicios));
        List<String> errores = errores(f, LocalDate.now(reloj));
        if (page < 0) errores.add("page: no puede ser negativo");
        if (size < 1 || size > TAMANO_MAXIMO) errores.add("size: entre 1 y " + TAMANO_MAXIMO);
        if (!errores.isEmpty()) {                                                    // CA-04
            throw ReglaNegocioException.solicitudInvalida("FILTROS_INVALIDOS",
                    "Revise los filtros de la búsqueda.", errores.toArray(String[]::new));
        }
        long[] total = new long[1];
        var items = repo.buscar(f, page, size, total);                               // CA-01, CA-02, CA-03, CA-06
        return new PaginaResultados(items, page, size, total[0]);                    // CA-05: lista vacía si no hay
    }

    /** Reglas de validación de los filtros (CA-04). Pública y estática para probarla sin base de datos. */
    public static List<String> errores(Filtros f, LocalDate hoy) {
        List<String> e = new ArrayList<>();
        if ((f.llegada() == null) != (f.salida() == null)) {
            e.add("llegada y salida: indique ambas fechas o ninguna");
        }
        if (f.llegada() != null && f.salida() != null) {
            if (!f.salida().isAfter(f.llegada())) e.add("salida: debe ser posterior a la llegada");
            if (f.llegada().isBefore(hoy)) e.add("llegada: no puede ser una fecha pasada");
        }
        if (f.huespedes() != null && f.huespedes() < 1) e.add("huespedes: debe ser al menos 1");
        if (f.tipo() != null && !ReglasAlojamiento.TIPOS.contains(f.tipo())) e.add("tipo: no válido");
        if (f.precioMin() != null && f.precioMin().signum() < 0) e.add("precioMin: no puede ser negativo");
        if (f.precioMin() != null && f.precioMax() != null && f.precioMin().compareTo(f.precioMax()) > 0) {
            e.add("precioMin: no puede ser mayor que precioMax");
        }
        return e;
    }

    // ------------------------------------------------------------------ HU-010
    /** CA-03: solo las publicaciones ACTIVAS se muestran al público; las demás responden «no encontrada». */
    public AlojamientoRespuesta detalle(UUID id) {
        Registro r = repo.buscar(id).filter(x -> ReglasAlojamiento.ACTIVA.equals(x.estado()))
                .orElseThrow(() -> ReglaNegocioException.noEncontrado(
                        "No es posible mostrar esta publicación: no existe o no está activa."));
        return consultas.respuesta(r, true);                                         // CA-01, CA-02
    }
}
