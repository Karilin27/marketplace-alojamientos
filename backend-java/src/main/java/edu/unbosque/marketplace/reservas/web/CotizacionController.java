package edu.unbosque.marketplace.reservas.web;

import edu.unbosque.marketplace.reservas.ReservaService;
import edu.unbosque.marketplace.reservas.web.Dtos.CotizacionRespuesta;
import edu.unbosque.marketplace.reservas.web.Dtos.CotizacionSolicitud;
import edu.unbosque.marketplace.seguridad.FiltroAutenticacion;
import edu.unbosque.marketplace.seguridad.Sesion;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** S3 — POST /api/quotes (HU-011). */
@RestController
@RequestMapping("/api/quotes")
public class CotizacionController {

    private final ReservaService servicio;

    public CotizacionController(ReservaService servicio) {
        this.servicio = servicio;
    }

    @PostMapping
    public CotizacionRespuesta cotizar(@RequestAttribute(FiltroAutenticacion.ATRIBUTO) Sesion sesion,
                                       @Valid @RequestBody CotizacionSolicitud cuerpo) {
        return servicio.cotizar(sesion, cuerpo);
    }
}
