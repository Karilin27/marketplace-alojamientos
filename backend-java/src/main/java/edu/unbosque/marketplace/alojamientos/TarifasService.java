package edu.unbosque.marketplace.alojamientos;

import edu.unbosque.marketplace.alojamientos.AlojamientoDtos.AlojamientoRespuesta;
import edu.unbosque.marketplace.alojamientos.AlojamientoDtos.PoliticaRespuesta;
import edu.unbosque.marketplace.alojamientos.AlojamientoDtos.TarifaServicio;
import edu.unbosque.marketplace.alojamientos.AlojamientoDtos.TarifasSolicitud;
import edu.unbosque.marketplace.alojamientos.AlojamientoRepositorio.Registro;
import edu.unbosque.marketplace.alojamientos.ReglasAlojamiento.Tarifas;
import edu.unbosque.marketplace.common.ReglaNegocioException;
import edu.unbosque.marketplace.logs.EventLogger;
import edu.unbosque.marketplace.seguridad.Sesion;
import edu.unbosque.marketplace.seguridad.Sesion.Rol;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * HU-023: políticas de cancelación disponibles (la publicación la elige con idPoliticaCancelacion).
 * HU-024: tarifa de limpieza, descuento por estancia, servicios adicionales y tarifa de servicio del marketplace.
 * Los valores guardados aquí son los que usa la cotización de S3 (fórmula de la página 06, D-04).
 */
@Service
public class TarifasService {

    private final AlojamientoRepositorio repo;
    private final AlojamientoConsultas consultas;
    private final TransactionTemplate tx;
    private final EventLogger log;

    public TarifasService(AlojamientoRepositorio repo, AlojamientoConsultas consultas, TransactionTemplate tx,
                          EventLogger log) {
        this.repo = repo;
        this.consultas = consultas;
        this.tx = tx;
        this.log = log;
    }

    // ------------------------------------------------------------------ HU-023 CA-01
    public List<PoliticaRespuesta> politicas() {
        return repo.politicas();
    }

    // ------------------------------------------------------------------ HU-024 CA-01, CA-02, CA-03, CA-05, CA-06
    public AlojamientoRespuesta configurar(Sesion sesion, UUID id, TarifasSolicitud solicitud) {
        sesion.exigirRol(Rol.ANFITRION, Rol.ADMINISTRADOR);
        if (solicitud == null) {
            throw ReglaNegocioException.solicitudInvalida("TARIFAS_INVALIDAS", "Debe enviar las tarifas.");
        }
        Tarifas t = ReglasAlojamiento.normalizarTarifas(solicitud);
        List<String> errores = ReglasAlojamiento.erroresTarifas(t);
        if (!errores.isEmpty()) {
            throw ReglaNegocioException.solicitudInvalida("TARIFAS_INVALIDAS",
                    "Hay valores de las tarifas que debe corregir.", errores.toArray(String[]::new));
        }
        tx.executeWithoutResult(s -> {
            Registro r = consultas.existente(id, true);
            consultas.exigirDuenoOAdmin(sesion, r);                                  // CA-06
            repo.actualizarTarifas(id, t.limpieza(), t.descuentoPct(), t.descuentoMinNoches());
            repo.sincronizarAdicionales(id, t.adicionales());
        });
        log.auditado("TARIFAS_CONFIGURADAS", sesion.id().toString(), "alojamiento", id.toString(), "EXITO",
                Map.of("tarifaLimpieza", t.limpieza(), "descuentoPct", t.descuentoPct(),
                        "descuentoMinNoches", t.descuentoMinNoches(), "serviciosAdicionales", t.adicionales().size()));
        return consultas.respuesta(id, false);
    }

    // ------------------------------------------------------------------ HU-024 CA-04 (D-04)
    public TarifaServicio tarifaServicio() {
        return new TarifaServicio(repo.tarifaServicioPct());
    }

    public TarifaServicio cambiarTarifaServicio(Sesion sesion, TarifaServicio solicitud) {
        sesion.exigirRol(Rol.ADMINISTRADOR);
        if (solicitud == null || !ReglasAlojamiento.tarifaServicioValida(solicitud.porcentaje())) {
            throw ReglaNegocioException.solicitudInvalida("TARIFA_SERVICIO_INVALIDA",
                    "La tarifa de servicio debe ser un porcentaje entre 0 y 100.", "porcentaje");
        }
        TarifaServicio anterior = tarifaServicio();
        repo.guardarTarifaServicioPct(solicitud.porcentaje());
        log.auditado("TARIFA_SERVICIO_CAMBIADA", sesion.id().toString(), "parametro_sistema", "tarifa_servicio_pct",
                "EXITO", Map.of("anterior", anterior.porcentaje(), "nueva", solicitud.porcentaje()));
        return tarifaServicio();
    }
}
