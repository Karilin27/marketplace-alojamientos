package edu.unbosque.marketplace.reservas;

import edu.unbosque.marketplace.common.ReglaNegocioException;
import edu.unbosque.marketplace.config.PropiedadesApp;
import edu.unbosque.marketplace.logs.EventLogger;
import edu.unbosque.marketplace.reservas.domain.Periodo;
import edu.unbosque.marketplace.reservas.domain.RangoFechas;
import edu.unbosque.marketplace.reservas.domain.ReglasDisponibilidad;
import edu.unbosque.marketplace.reservas.persistencia.CatalogoRepositorio;
import edu.unbosque.marketplace.reservas.persistencia.DisponibilidadRepositorio;
import edu.unbosque.marketplace.reservas.web.Dtos.DisponibilidadRespuesta;
import edu.unbosque.marketplace.reservas.web.Dtos.PeriodoDto;
import edu.unbosque.marketplace.seguridad.Sesion;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** HU-008: consulta y administración de la disponibilidad de un alojamiento. */
@Service
public class DisponibilidadService {

    private static final int DIAS_CONSULTA_POR_DEFECTO = 365;

    private final CatalogoRepositorio catalogo;
    private final DisponibilidadRepositorio repo;
    private final PropiedadesApp props;
    private final Clock reloj;
    private final EventLogger log;

    public DisponibilidadService(CatalogoRepositorio catalogo, DisponibilidadRepositorio repo, PropiedadesApp props,
                                 Clock reloj, EventLogger log) {
        this.catalogo = catalogo;
        this.repo = repo;
        this.props = props;
        this.reloj = reloj;
        this.log = log;
    }

    /** Público: periodos declarados + periodos RESERVADO (sin datos del huésped). */
    public DisponibilidadRespuesta consultar(UUID idAlojamiento, LocalDate desde, LocalDate hasta) {
        catalogo.buscar(idAlojamiento, props.tarifaServicioPctPorDefecto())
                .orElseThrow(() -> ReglaNegocioException.noEncontrado("El alojamiento no existe."));
        LocalDate d = desde != null ? desde : LocalDate.now(reloj);
        LocalDate h = hasta != null ? hasta : d.plusDays(DIAS_CONSULTA_POR_DEFECTO);
        if (!h.isAfter(d)) {
            throw ReglaNegocioException.regla("FECHAS_INVALIDAS", "El parámetro 'hasta' debe ser posterior a 'desde'.");
        }
        List<PeriodoDto> resultado = new ArrayList<>();
        repo.periodos(idAlojamiento, d, h).forEach(p -> resultado.add(PeriodoDto.de(p)));
        for (RangoFechas r : repo.reservados(idAlojamiento, d, h, reloj.instant())) {
            resultado.add(new PeriodoDto(r.llegada(), r.salida(), Periodo.Tipo.RESERVADO));
        }
        resultado.sort(java.util.Comparator.comparing(PeriodoDto::fechaInicio));
        return new DisponibilidadRespuesta(idAlojamiento, resultado);
    }

    /** Solo el anfitrión propietario (o un administrador). Reemplaza los periodos declarados. */
    @Transactional
    public DisponibilidadRespuesta reemplazar(Sesion sesion, UUID idAlojamiento, List<Periodo> periodos) {
        sesion.exigirRol(Sesion.Rol.ANFITRION, Sesion.Rol.ADMINISTRADOR);
        var aloj = catalogo.buscar(idAlojamiento, props.tarifaServicioPctPorDefecto())
                .orElseThrow(() -> ReglaNegocioException.noEncontrado("El alojamiento no existe."));
        if (!sesion.esAdmin() && !aloj.idAnfitrion().equals(sesion.id())) {
            throw ReglaNegocioException.prohibido("Solo el anfitrión propietario puede modificar la disponibilidad.");
        }
        LocalDate hoy = LocalDate.now(reloj);
        List<Periodo> validos = ReglasDisponibilidad.validar(periodos, hoy);

        // CA-04: no se puede marcar como no disponible un periodo con reservas CONFIRMADA o PENDIENTE_PAGO vigentes.
        List<String> choques = new ArrayList<>();
        for (Periodo p : validos) {
            if (p.tipo() == Periodo.Tipo.NO_DISPONIBLE) {
                var reservas = repo.reservados(idAlojamiento, p.inicio(), p.fin(), reloj.instant());
                for (RangoFechas r : ReglasDisponibilidad.choques(p.rango(), reservas)) {
                    choques.add("Reserva del " + r.llegada() + " al " + r.salida());
                }
            }
        }
        if (!choques.isEmpty()) {
            throw ReglaNegocioException.conflicto("PERIODO_CON_RESERVAS",
                    "El periodo se cruza con reservas existentes y no puede marcarse como no disponible.",
                    choques.toArray(String[]::new));
        }
        repo.reemplazar(idAlojamiento, validos);
        log.auditado("DISPONIBILIDAD_ACTUALIZADA", sesion.id().toString(), "alojamiento", idAlojamiento.toString(),
                "EXITO", Map.of("periodos", validos.size()));
        return new DisponibilidadRespuesta(idAlojamiento, validos.stream().map(PeriodoDto::de).toList());
    }
}
