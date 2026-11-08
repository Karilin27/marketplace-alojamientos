package edu.unbosque.marketplace.reservas;

import edu.unbosque.marketplace.common.ReglaNegocioException;
import edu.unbosque.marketplace.config.PropiedadesApp;
import edu.unbosque.marketplace.logs.EventLogger;
import edu.unbosque.marketplace.notificaciones.NotificacionService;
import edu.unbosque.marketplace.pagos.PagoService;
import edu.unbosque.marketplace.reservas.domain.CalculadoraCotizacion;
import edu.unbosque.marketplace.reservas.domain.Cotizacion;
import edu.unbosque.marketplace.reservas.domain.EstadoReserva;
import edu.unbosque.marketplace.reservas.domain.RangoFechas;
import edu.unbosque.marketplace.reservas.domain.ReglasDisponibilidad;
import edu.unbosque.marketplace.reservas.domain.ServicioAdicional;
import edu.unbosque.marketplace.reservas.domain.TokenCotizacion;
import edu.unbosque.marketplace.reservas.persistencia.CatalogoRepositorio;
import edu.unbosque.marketplace.reservas.persistencia.CatalogoRepositorio.Alojamiento;
import edu.unbosque.marketplace.reservas.persistencia.DisponibilidadRepositorio;
import edu.unbosque.marketplace.reservas.persistencia.Reserva;
import edu.unbosque.marketplace.reservas.persistencia.ReservaRepositorio;
import edu.unbosque.marketplace.reservas.web.Dtos.CotizacionRespuesta;
import edu.unbosque.marketplace.reservas.web.Dtos.CotizacionSolicitud;
import edu.unbosque.marketplace.reservas.web.Dtos.Pagina;
import edu.unbosque.marketplace.reservas.web.Dtos.ReservaRespuesta;
import edu.unbosque.marketplace.seguridad.Sesion;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** HU-011 cotizar, HU-012 reservar, HU-014 consultar, HU-015 cancelar. */
@Service
public class ReservaService {

    private final CatalogoRepositorio catalogo;
    private final DisponibilidadRepositorio disponibilidad;
    private final ReservaRepositorio reservas;
    private final PagoService pagos;
    private final NotificacionService notificaciones;
    private final TransactionTemplate tx;
    private final PropiedadesApp props;
    private final Clock reloj;
    private final EventLogger log;
    private final byte[] secretoCotizacion;

    public ReservaService(CatalogoRepositorio catalogo, DisponibilidadRepositorio disponibilidad,
                          ReservaRepositorio reservas, PagoService pagos, NotificacionService notificaciones,
                          TransactionTemplate tx, PropiedadesApp props, Clock reloj, EventLogger log) {
        this.catalogo = catalogo;
        this.disponibilidad = disponibilidad;
        this.reservas = reservas;
        this.pagos = pagos;
        this.notificaciones = notificaciones;
        this.tx = tx;
        this.props = props;
        this.reloj = reloj;
        this.log = log;
        if (props.cotizacionSecret() == null || props.cotizacionSecret().length() < 32) {
            throw new IllegalStateException("marketplace.cotizacion-secret debe tener al menos 32 caracteres.");
        }
        this.secretoCotizacion = props.cotizacionSecret().getBytes(StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ cotización (HU-011)

    private record Calculo(Alojamiento alojamiento, Cotizacion cotizacion, List<ServicioAdicional> servicios,
                           RangoFechas rango) {
    }

    private Calculo calcular(UUID idAlojamiento, LocalDate llegada, LocalDate salida, int huespedes,
                             List<String> idsServicios) {
        RangoFechas rango = RangoFechas.validado(llegada, salida, LocalDate.now(reloj));
        BigDecimal pct = catalogo.tarifaServicioPct().orElse(props.tarifaServicioPctPorDefecto());
        Alojamiento aloj = catalogo.buscar(idAlojamiento, pct).filter(Alojamiento::publicado)
                .orElseThrow(() -> ReglaNegocioException.noEncontrado("El alojamiento no existe o no está publicado."));

        List<String> ids = idsServicios == null ? List.of() : idsServicios.stream().distinct().toList();
        List<ServicioAdicional> activos = catalogo.serviciosActivos(idAlojamiento);
        List<ServicioAdicional> elegidos = ids.stream().map(id -> activos.stream()
                .filter(s -> s.id().equals(id)).findFirst()
                .orElseThrow(() -> ReglaNegocioException.regla("SERVICIO_NO_VALIDO",
                        "El servicio adicional " + id + " no pertenece al alojamiento o no está activo."))).toList();

        Cotizacion cot = CalculadoraCotizacion.calcular(aloj.tarifa(), rango, huespedes, elegidos);

        boolean libre = ReglasDisponibilidad.libre(rango, disponibilidad.periodos(idAlojamiento, llegada, salida),
                disponibilidad.reservados(idAlojamiento, llegada, salida, reloj.instant()));
        if (!libre) {
            throw ReglaNegocioException.conflicto("FECHAS_NO_DISPONIBLES",
                    "El alojamiento no está disponible en las fechas solicitadas.");
        }
        return new Calculo(aloj, cot, elegidos, rango);
    }

    public CotizacionRespuesta cotizar(Sesion sesion, CotizacionSolicitud s) {
        sesion.exigirRol(Sesion.Rol.HUESPED);
        Calculo c = calcular(s.idAlojamiento(), s.fechaLlegada(), s.fechaSalida(), s.huespedes(),
                s.idsServiciosAdicionales());
        Instant vence = reloj.instant().plus(TokenCotizacion.VIGENCIA);
        String token = TokenCotizacion.emitir(new TokenCotizacion.Datos(s.idAlojamiento().toString(), c.rango(),
                s.huespedes(), c.servicios().stream().map(ServicioAdicional::id).toList(), c.cotizacion().total(),
                sesion.id().toString(), vence), secretoCotizacion);
        log.info("COTIZACION_EMITIDA", sesion.id().toString(), "alojamiento", s.idAlojamiento().toString(), "EXITO",
                Map.of("total", c.cotizacion().total(), "noches", c.cotizacion().noches()));
        return new CotizacionRespuesta(s.idAlojamiento(), s.fechaLlegada(), s.fechaSalida(), s.huespedes(),
                c.cotizacion(), c.alojamiento().politica(), token, vence);
    }

    // ------------------------------------------------------------------ crear reserva (HU-012)

    public ReservaRespuesta crear(Sesion sesion, String tokenCotizacion, String llave) {
        sesion.exigirRol(Sesion.Rol.HUESPED);
        exigirLlave(llave);

        // Reintento con la misma llave: se devuelve la reserva original (RN-12), aunque el token ya venciera.
        TokenCotizacion.Datos leido = TokenCotizacion.verificar(tokenCotizacion, secretoCotizacion, Instant.EPOCH);
        var previa = reservas.buscarPorLlave(sesion.id(), llave);
        if (previa.isPresent()) {
            return repetida(previa.get(), leido);
        }

        TokenCotizacion.Datos datos = TokenCotizacion.verificar(tokenCotizacion, secretoCotizacion, reloj.instant());
        if (!datos.idHuesped().equals(sesion.id().toString())) {
            throw ReglaNegocioException.prohibido("La cotización pertenece a otro usuario.");
        }
        try {
            Reserva creada = tx.execute(st -> insertar(sesion, datos, llave));
            return respuesta(creada);
        } catch (DuplicateKeyException e) {
            // dos solicitudes simultáneas con la misma llave: gana la primera
            return reservas.buscarPorLlave(sesion.id(), llave).map(r -> repetida(r, datos)).orElseThrow(() -> e);
        } catch (DataIntegrityViolationException e) {
            if (esExclusion(e)) {
                log.warn("RESERVA_CONFLICTO_FECHAS", sesion.id().toString(), "alojamiento", datos.idAlojamiento(),
                        "FALLO", Map.of());
                throw ReglaNegocioException.conflicto("FECHAS_NO_DISPONIBLES",
                        "Otra reserva tomó esas fechas mientras confirmabas. Elige otras fechas.");
            }
            throw e;
        }
    }

    private Reserva insertar(Sesion sesion, TokenCotizacion.Datos datos, String llave) {
        Instant ahora = reloj.instant();
        reservas.expirarVencidas(ahora); // libera retenciones vencidas que el job aún no procesó
        UUID idAloj = UUID.fromString(datos.idAlojamiento());
        Calculo c = calcular(idAloj, datos.rango().llegada(), datos.rango().salida(), datos.huespedes(),
                datos.idsServicios());
        if (c.alojamiento().idAnfitrion().equals(sesion.id())) {
            throw ReglaNegocioException.regla("RESERVA_PROPIA", "No puedes reservar tu propio alojamiento.");
        }
        if (c.cotizacion().total().compareTo(datos.total()) != 0) {
            throw ReglaNegocioException.conflicto("COTIZACION_DESACTUALIZADA",
                    "Los precios cambiaron desde la cotización; solicita una nueva.");
        }
        Cotizacion t = c.cotizacion();
        Reserva r = new Reserva(UUID.randomUUID(), idAloj, sesion.id(), c.rango(), datos.huespedes(),
                EstadoReserva.PENDIENTE_PAGO, t.subtotal(), t.descuento(), t.tarifaLimpieza(), t.tarifaServicio(),
                t.total(), c.alojamiento().politica(), llave, false,
                ahora.plusSeconds(props.retencionPagoMinutos() * 60L), ahora);
        reservas.insertar(r, t.serviciosAdicionales());
        notificaciones.notificar(sesion.id(), NotificacionService.RESERVA_CREADA,
                "Reservaste del " + r.rango().llegada() + " al " + r.rango().salida() + ". Tienes "
                        + props.retencionPagoMinutos() + " minutos para pagar.", r.id());
        log.auditado("RESERVA_CREADA", sesion.id().toString(), "reserva", r.id().toString(), "EXITO",
                Map.of("alojamiento", idAloj.toString(), "total", r.total(), "noches", t.noches()));
        return r;
    }

    private ReservaRespuesta repetida(Reserva previa, TokenCotizacion.Datos datos) {
        if (!previa.idAlojamiento().toString().equals(datos.idAlojamiento())
                || !previa.rango().equals(datos.rango())) {
            throw ReglaNegocioException.conflicto("LLAVE_REUTILIZADA",
                    "La Idempotency-Key ya se usó para una reserva distinta.");
        }
        return respuesta(previa);
    }

    // ------------------------------------------------------------------ consultar (HU-014)

    public ReservaRespuesta obtener(Sesion sesion, UUID id) {
        Reserva r = reservas.buscar(id).orElseThrow(() -> ReglaNegocioException.noEncontrado("La reserva no existe."));
        boolean propietario = r.idHuesped().equals(sesion.id());
        boolean anfitrion = catalogo.buscar(r.idAlojamiento(), props.tarifaServicioPctPorDefecto())
                .map(a -> a.idAnfitrion().equals(sesion.id())).orElse(false);
        if (!propietario && !anfitrion && !sesion.esAdmin()) {
            throw ReglaNegocioException.prohibido("No tienes acceso a esta reserva.");
        }
        return respuesta(r);
    }

    public Pagina<ReservaRespuesta> listarDelHuesped(Sesion sesion, int pagina, int tamano) {
        sesion.exigirRol(Sesion.Rol.HUESPED);
        int p = Math.max(pagina, 0);
        int t = Math.min(Math.max(tamano, 1), 100);
        return new Pagina<>(reservas.listarPorHuesped(sesion.id(), p, t).stream().map(this::respuesta).toList(), p, t,
                reservas.contarPorHuesped(sesion.id()));
    }

    public Pagina<ReservaRespuesta> listarRecibidas(Sesion sesion, int pagina, int tamano) {
        sesion.exigirRol(Sesion.Rol.ANFITRION);
        int p = Math.max(pagina, 0);
        int t = Math.min(Math.max(tamano, 1), 100);
        return new Pagina<>(reservas.listarPorAnfitrion(sesion.id(), p, t).stream().map(this::respuesta).toList(), p,
                t, reservas.contarPorAnfitrion(sesion.id()));
    }

    // ------------------------------------------------------------------ cancelar (HU-015)

    public ReservaRespuesta cancelar(Sesion sesion, UUID id, String motivo, String llave) {
        sesion.exigirRol(Sesion.Rol.HUESPED);
        exigirLlave(llave);
        var previa = reservas.reservaDeCancelacionPorLlave(sesion.id(), llave);
        if (previa.isPresent()) {
            if (!previa.get().equals(id)) {
                throw ReglaNegocioException.conflicto("LLAVE_REUTILIZADA",
                        "La Idempotency-Key ya se usó para cancelar otra reserva.");
            }
            return respuesta(reservas.buscar(id).orElseThrow());
        }
        BigDecimal devolucion;
        try {
            devolucion = tx.execute(st -> registrarCancelacion(sesion, id, motivo, llave));
        } catch (DuplicateKeyException e) {
            return reservas.reservaDeCancelacionPorLlave(sesion.id(), llave)
                    .map(x -> respuesta(reservas.buscar(x).orElseThrow())).orElseThrow(() -> e);
        }
        boolean reembolsado = pagos.reembolsar(id, devolucion, sesion.id());
        log.auditado("RESERVA_CANCELADA", sesion.id().toString(), "reserva", id.toString(), "EXITO",
                Map.of("reembolso", devolucion, "reembolsoRegistrado", reembolsado));
        return respuesta(reservas.buscar(id).orElseThrow());
    }

    private BigDecimal registrarCancelacion(Sesion sesion, UUID id, String motivo, String llave) {
        Reserva r = reservas.bloquear(id).orElseThrow(() -> ReglaNegocioException.noEncontrado("La reserva no existe."));
        if (!r.idHuesped().equals(sesion.id())) {
            throw ReglaNegocioException.prohibido("Solo el huésped que hizo la reserva puede cancelarla.");
        }
        LocalDate hoy = LocalDate.now(reloj);
        if (r.estado() != EstadoReserva.CONFIRMADA) {
            throw ReglaNegocioException.conflicto("RESERVA_NO_CANCELABLE",
                    "Solo se pueden cancelar reservas CONFIRMADA; esta está " + r.estado() + ".");
        }
        // lanza CANCELACION_NO_PERMITIDA si la fecha de llegada ya pasó
        BigDecimal devolucion = r.politica().calcularDevolucion(r.total(), hoy, r.rango().llegada());
        if (!reservas.cambiarEstado(id, EstadoReserva.CONFIRMADA, EstadoReserva.CANCELADA, sesion.id(), motivo)) {
            throw ReglaNegocioException.conflicto("RESERVA_NO_CANCELABLE", "La reserva cambió de estado.");
        }
        reservas.insertarCancelacion(id, sesion.id(), motivo, devolucion, llave, reloj.instant());
        notificaciones.notificar(r.idHuesped(), NotificacionService.RESERVA_CANCELADA,
                "Cancelaste tu reserva. Reembolso: " + devolucion.toPlainString() + " "
                        + props.moneda().toUpperCase() + ".", id);
        catalogo.buscar(r.idAlojamiento(), props.tarifaServicioPctPorDefecto()).ifPresent(a ->
                notificaciones.notificar(a.idAnfitrion(), NotificacionService.RESERVA_CANCELADA,
                        "Se canceló la reserva del " + r.rango().llegada() + " al " + r.rango().salida()
                                + "; las fechas quedaron libres.", id));
        return devolucion;
    }

    // ------------------------------------------------------------------ utilidades

    private ReservaRespuesta respuesta(Reserva r) {
        return ReservaRespuesta.de(r, reservas.servicios(r.id()), reservas.montoReembolso(r.id()).orElse(null));
    }

    private static void exigirLlave(String llave) {
        if (llave == null || llave.isBlank() || llave.length() > 100) {
            throw ReglaNegocioException.solicitudInvalida("LLAVE_REQUERIDA",
                    "Idempotency-Key es obligatoria (máximo 100 caracteres).");
        }
    }

    private static boolean esExclusion(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException s && "23P01".equals(s.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
