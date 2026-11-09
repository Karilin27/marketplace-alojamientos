package edu.unbosque.marketplace.pagos;

import edu.unbosque.marketplace.common.ReglaNegocioException;
import edu.unbosque.marketplace.config.PropiedadesApp;
import edu.unbosque.marketplace.logs.EventLogger;
import edu.unbosque.marketplace.notificaciones.NotificacionService;
import edu.unbosque.marketplace.pagos.PagoDtos.PagoRespuesta;
import edu.unbosque.marketplace.pagos.PagoDtos.PagoSolicitud;
import edu.unbosque.marketplace.pagos.PagoRepositorio.Pago;
import edu.unbosque.marketplace.reservas.domain.EstadoReserva;
import edu.unbosque.marketplace.reservas.persistencia.CatalogoRepositorio;
import edu.unbosque.marketplace.reservas.persistencia.Reserva;
import edu.unbosque.marketplace.reservas.persistencia.ReservaRepositorio;
import edu.unbosque.marketplace.seguridad.Sesion;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * HU-013 (pago simulado/de prueba) y reembolsos de HU-015. Flujo del pago, pensado para ser idempotente (RN-12):
 * 1) tx corta: valida y registra el pago PENDIENTE con la llave (la restricción única evita dobles cobros);
 * 2) cobro en la pasarela, fuera de transacción; 3) tx corta: aplica el resultado a pago y reserva.
 */
@Service
public class PagoService {

    private final PasarelaPago pasarela;
    private final PagoRepositorio pagos;
    private final ReservaRepositorio reservas;
    private final CatalogoRepositorio catalogo;
    private final NotificacionService notificaciones;
    private final TransactionTemplate tx;
    private final PropiedadesApp props;
    private final Clock reloj;
    private final EventLogger log;

    public PagoService(PasarelaPago pasarela, PagoRepositorio pagos, ReservaRepositorio reservas,
                       CatalogoRepositorio catalogo, NotificacionService notificaciones, TransactionTemplate tx,
                       PropiedadesApp props, Clock reloj, EventLogger log) {
        this.pasarela = pasarela;
        this.pagos = pagos;
        this.reservas = reservas;
        this.catalogo = catalogo;
        this.notificaciones = notificaciones;
        this.tx = tx;
        this.props = props;
        this.reloj = reloj;
        this.log = log;
    }

    public PagoRespuesta pagar(Sesion sesion, PagoSolicitud solicitud, String llave) {
        sesion.exigirRol(Sesion.Rol.HUESPED);
        if (llave == null || llave.isBlank()) {
            throw ReglaNegocioException.solicitudInvalida("LLAVE_REQUERIDA", "Idempotency-Key es obligatoria.");
        }
        UUID idPago = UUID.randomUUID();

        // 1) validar y registrar el intento
        Object previo;
        try {
            previo = tx.execute(st -> iniciar(sesion, solicitud, llave, idPago));
        } catch (DuplicateKeyException e) {
            // Se captura fuera de la transacción (en PostgreSQL una transacción con error queda abortada):
            // otra solicitud con la misma llave ganó la carrera, o ya hay un pago en curso para la reserva.
            var ganador = pagos.buscarPorLlave(sesion.id(), llave);
            if (ganador.isPresent()) {
                return respuesta(ganador.get());
            }
            throw ReglaNegocioException.conflicto("PAGO_EN_CURSO", "Ya hay un pago en curso para esta reserva.");
        }
        if (previo instanceof PagoRespuesta repetido) {
            return repetido; // misma Idempotency-Key: se devuelve el resultado original
        }
        Reserva reserva = (Reserva) previo;

        // 2) cobro (sin transacción abierta)
        PasarelaPago.Cobro cobro;
        try {
            cobro = pasarela.cobrar(reserva.total(), props.moneda(), solicitud.metodoPago(), idPago.toString());
        } catch (RuntimeException e) {
            // resultado desconocido: no se asume ni aprobado ni rechazado, queda para revisión manual (RN-19)
            reservas.marcarRevisionManual(reserva.id());
            log.error("PAGO_ERROR_PASARELA", sesion.id().toString(), "pago", idPago.toString(), "FALLO",
                    Map.of("reserva", reserva.id().toString(), "causa", e.getClass().getSimpleName()));
            throw e;
        }

        // 3) aplicar el resultado
        Resultado r = tx.execute(st -> aplicar(sesion, reserva, idPago, cobro));
        if (r.rechazo() != null) {
            throw r.rechazo();
        }
        return r.respuesta();
    }

    private record Resultado(PagoRespuesta respuesta, ReglaNegocioException rechazo) {
    }

    private Object iniciar(Sesion sesion, PagoSolicitud solicitud, String llave, UUID idPago) {
        var existente = pagos.buscarPorLlave(sesion.id(), llave);
        if (existente.isPresent()) {
            if (!existente.get().idReserva().equals(solicitud.idReserva())) {
                throw ReglaNegocioException.conflicto("LLAVE_REUTILIZADA",
                        "La Idempotency-Key ya se usó para otra reserva.");
            }
            return respuesta(existente.get());
        }
        Reserva r = reservas.bloquear(solicitud.idReserva())
                .orElseThrow(() -> ReglaNegocioException.noEncontrado("La reserva no existe."));
        if (!r.idHuesped().equals(sesion.id())) {
            throw ReglaNegocioException.prohibido("Solo el huésped que hizo la reserva puede pagarla.");
        }
        if (r.estado() != EstadoReserva.PENDIENTE_PAGO) {
            throw ReglaNegocioException.conflicto("RESERVA_NO_PAGABLE",
                    "La reserva está en estado " + r.estado() + " y no admite pago.");
        }
        if (!r.expiraEn().isAfter(reloj.instant())) {
            throw ReglaNegocioException.conflicto("RETENCION_VENCIDA",
                    "Venció el tiempo para pagar; vuelva a cotizar y reservar.");
        }
        pagos.insertarPendiente(idPago, r.id(), sesion.id(), pasarela.nombre(), r.total(), llave);
        return r;
    }

    private Resultado aplicar(Sesion sesion, Reserva inicial, UUID idPago, PasarelaPago.Cobro cobro) {
        Reserva r = reservas.bloquear(inicial.id()).orElseThrow();
        String usuario = sesion.id().toString();
        boolean sigueVigente = r.estado() == EstadoReserva.PENDIENTE_PAGO;

        if (cobro.aprobado() && !sigueVigente) {
            // Se cobró pero la reserva venció mientras tanto: se devuelve todo y se informa (sin dejar dinero retenido).
            pagos.resolver(idPago, "APROBADO", cobro.idOperacion(), null);
            try {
                devolver(pagos.buscar(idPago).orElseThrow(), r.total(), r);
            } catch (RuntimeException e) {
                reservas.marcarRevisionManual(r.id());
                log.error("REEMBOLSO_FALLIDO", usuario, "pago", idPago.toString(), "FALLO",
                        Map.of("reserva", r.id().toString(), "causa", e.getClass().getSimpleName()));
            }
            log.auditado("PAGO_APROBADO_CON_RESERVA_VENCIDA", usuario, "pago", idPago.toString(), "FALLO",
                    Map.of("reserva", r.id().toString()));
            return new Resultado(null, ReglaNegocioException.conflicto("RETENCION_VENCIDA",
                    "La reserva venció antes de confirmarse el pago; el cobro fue reembolsado."));
        }
        if (cobro.aprobado()) {
            pagos.resolver(idPago, "APROBADO", cobro.idOperacion(), null);
            reservas.cambiarEstado(r.id(), EstadoReserva.PENDIENTE_PAGO, EstadoReserva.CONFIRMADA, sesion.id(),
                    "Pago aprobado");
            var aloj = catalogo.buscar(r.idAlojamiento(), props.tarifaServicioPctPorDefecto());
            notificaciones.notificar(r.idHuesped(), NotificacionService.PAGO_APROBADO,
                    "Tu pago fue aprobado y tu reserva del " + r.rango().llegada() + " al " + r.rango().salida()
                            + " quedó confirmada.", r.id());
            aloj.ifPresent(a -> notificaciones.notificar(a.idAnfitrion(), NotificacionService.RESERVA_RECIBIDA,
                    "Tienes una nueva reserva confirmada del " + r.rango().llegada() + " al " + r.rango().salida()
                            + ".", r.id()));
            log.auditado("PAGO_APROBADO", usuario, "pago", idPago.toString(), "EXITO",
                    Map.of("reserva", r.id().toString(), "monto", r.total()));
            return new Resultado(respuesta(pagos.buscar(idPago).orElseThrow()), null);
        }
        // rechazado
        pagos.resolver(idPago, "RECHAZADO", cobro.idOperacion(), cobro.motivoRechazo());
        if (sigueVigente) {
            reservas.cambiarEstado(r.id(), EstadoReserva.PENDIENTE_PAGO, EstadoReserva.RECHAZADA, sesion.id(),
                    "Pago rechazado");
        }
        notificaciones.notificar(r.idHuesped(), NotificacionService.PAGO_RECHAZADO,
                "Tu pago fue rechazado (" + cobro.motivoRechazo() + "). La reserva no se realizó.", r.id());
        log.auditado("PAGO_RECHAZADO", usuario, "pago", idPago.toString(), "FALLO",
                Map.of("reserva", r.id().toString(), "motivo", String.valueOf(cobro.motivoRechazo())));
        return new Resultado(null, ReglaNegocioException.regla("PAGO_RECHAZADO",
                "El pago fue rechazado: " + cobro.motivoRechazo(), cobro.motivoRechazo()));
    }

    /**
     * Reembolso total o parcial de un pago aprobado (cancelación, HU-015). Nunca lanza excepción: si el
     * proveedor falla deja la reserva en revisión manual (RN-19) y lo registra.
     * @return true si el reembolso quedó registrado.
     */
    public boolean reembolsar(UUID idReserva, BigDecimal monto, UUID actor) {
        if (monto.signum() <= 0) {
            return true;
        }
        Reserva r = reservas.buscar(idReserva).orElseThrow();
        Pago pago = pagos.aprobadoDeReserva(idReserva).orElse(null);
        if (pago == null) {
            reservas.marcarRevisionManual(idReserva);
            log.error("REEMBOLSO_SIN_PAGO_APROBADO", actor.toString(), "reserva", idReserva.toString(), "FALLO", Map.of());
            return false;
        }
        try {
            tx.executeWithoutResult(st -> devolver(pago, monto, r));
            return true;
        } catch (RuntimeException e) {
            reservas.marcarRevisionManual(idReserva);
            log.error("REEMBOLSO_FALLIDO", actor.toString(), "pago", pago.id().toString(), "FALLO",
                    Map.of("reserva", idReserva.toString(), "causa", e.getClass().getSimpleName()));
            return false;
        }
    }

    private void devolver(Pago pago, BigDecimal monto, Reserva r) {
        pasarela.reembolsar(pago.idOperacionExterna(), monto, props.moneda(), pago.id() + "-" + monto.toPlainString());
        String estado = monto.compareTo(pago.monto()) >= 0 ? "REEMBOLSADO" : "REEMBOLSO_PARCIAL";
        pagos.registrarReembolso(pago.id(), estado, monto);
        notificaciones.notificar(r.idHuesped(), NotificacionService.REEMBOLSO,
                "Se reembolsó " + monto.toPlainString() + " " + props.moneda().toUpperCase() + " por tu reserva.",
                r.id());
        log.auditado("REEMBOLSO_REGISTRADO", r.idHuesped().toString(), "pago", pago.id().toString(), "EXITO",
                Map.of("reserva", r.id().toString(), "monto", monto));
    }

    private PagoRespuesta respuesta(Pago p) {
        String estadoReserva = reservas.buscar(p.idReserva()).map(x -> x.estado().name()).orElse(null);
        return new PagoRespuesta(p.id(), p.idReserva(), p.proveedor(), p.monto(), p.estado(), p.idOperacionExterna(),
                estadoReserva, p.fecha());
    }
}
