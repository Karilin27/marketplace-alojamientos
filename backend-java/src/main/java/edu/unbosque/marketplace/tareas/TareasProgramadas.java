package edu.unbosque.marketplace.tareas;

import edu.unbosque.marketplace.logs.EventLogger;
import edu.unbosque.marketplace.notificaciones.NotificacionService;
import edu.unbosque.marketplace.reservas.persistencia.CatalogoRepositorio;
import edu.unbosque.marketplace.reservas.persistencia.ReservaRepositorio;
import edu.unbosque.marketplace.config.PropiedadesApp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Vencimiento de la retención de pago (RN-10) y cierre de estancias (CONFIRMADA -> COMPLETADA). */
@Component
public class TareasProgramadas {

    private final ReservaRepositorio reservas;
    private final CatalogoRepositorio catalogo;
    private final NotificacionService notificaciones;
    private final TransactionTemplate tx;
    private final PropiedadesApp props;
    private final Clock reloj;
    private final EventLogger log;

    public TareasProgramadas(ReservaRepositorio reservas, CatalogoRepositorio catalogo,
                             NotificacionService notificaciones, TransactionTemplate tx, PropiedadesApp props,
                             Clock reloj, EventLogger log) {
        this.reservas = reservas;
        this.catalogo = catalogo;
        this.notificaciones = notificaciones;
        this.tx = tx;
        this.props = props;
        this.reloj = reloj;
        this.log = log;
    }

    @Scheduled(fixedDelayString = "${marketplace.tareas.expiracion-ms:60000}")
    public void expirarRetenciones() {
        tx.executeWithoutResult(st -> reservas.expirarVencidas(reloj.instant()).forEach(r -> {
            notificaciones.notificar(r.idHuesped(), NotificacionService.RESERVA_EXPIRADA,
                    "Tu reserva del " + r.rango().llegada() + " al " + r.rango().salida()
                            + " venció por falta de pago y las fechas quedaron libres.", r.id());
            log.auditado("RESERVA_EXPIRADA", "sistema", "reserva", r.id().toString(), "EXITO", Map.of());
        }));
    }

    @Scheduled(cron = "${marketplace.tareas.completar-cron:0 5 0 * * *}", zone = "${marketplace.zona:America/Bogota}")
    public void completarEstancias() {
        tx.executeWithoutResult(st -> reservas.completarFinalizadas(LocalDate.now(reloj)).forEach(r -> {
            notificaciones.notificar(r.idHuesped(), NotificacionService.RESERVA_COMPLETADA,
                    "Tu estancia terminó. ¡Gracias por elegirnos!", r.id());
            catalogo.buscar(r.idAlojamiento(), props.tarifaServicioPctPorDefecto()).ifPresent(a ->
                    notificaciones.notificar(a.idAnfitrion(), NotificacionService.RESERVA_COMPLETADA,
                            "Terminó la estancia del " + r.rango().llegada() + " al " + r.rango().salida() + ".",
                            r.id()));
            log.auditado("RESERVA_COMPLETADA", "sistema", "reserva", r.id().toString(), "EXITO", Map.of());
        }));
    }
}
