package edu.unbosque.marketplace.reservas.web;

import edu.unbosque.marketplace.reservas.ReservaService;
import edu.unbosque.marketplace.reservas.web.Dtos.CancelacionSolicitud;
import edu.unbosque.marketplace.reservas.web.Dtos.Pagina;
import edu.unbosque.marketplace.reservas.web.Dtos.ReservaRespuesta;
import edu.unbosque.marketplace.reservas.web.Dtos.ReservaSolicitud;
import edu.unbosque.marketplace.seguridad.FiltroAutenticacion;
import edu.unbosque.marketplace.seguridad.Sesion;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** S3 — /api/bookings (HU-012, HU-014, HU-015). */
@RestController
@RequestMapping("/api/bookings")
public class ReservaController {

    private final ReservaService servicio;

    public ReservaController(ReservaService servicio) {
        this.servicio = servicio;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservaRespuesta crear(@RequestAttribute(FiltroAutenticacion.ATRIBUTO) Sesion sesion,
                                  @RequestHeader("Idempotency-Key") String llave,
                                  @Valid @RequestBody ReservaSolicitud cuerpo) {
        return servicio.crear(sesion, cuerpo.tokenCotizacion(), llave);
    }

    @GetMapping
    public Pagina<ReservaRespuesta> misReservas(@RequestAttribute(FiltroAutenticacion.ATRIBUTO) Sesion sesion,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return servicio.listarDelHuesped(sesion, page, size);
    }

    @GetMapping("/received")
    public Pagina<ReservaRespuesta> recibidas(@RequestAttribute(FiltroAutenticacion.ATRIBUTO) Sesion sesion,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return servicio.listarRecibidas(sesion, page, size);
    }

    @GetMapping("/{id}")
    public ReservaRespuesta detalle(@RequestAttribute(FiltroAutenticacion.ATRIBUTO) Sesion sesion,
                                    @PathVariable UUID id) {
        return servicio.obtener(sesion, id);
    }

    @PostMapping("/{id}/cancel")
    public ReservaRespuesta cancelar(@RequestAttribute(FiltroAutenticacion.ATRIBUTO) Sesion sesion,
                                     @PathVariable UUID id,
                                     @RequestHeader("Idempotency-Key") String llave,
                                     @RequestBody(required = false) CancelacionSolicitud cuerpo) {
        return servicio.cancelar(sesion, id, cuerpo == null ? null : cuerpo.motivo(), llave);
    }
}
