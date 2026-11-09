package edu.unbosque.marketplace.pagos;

import edu.unbosque.marketplace.pagos.PagoDtos.PagoRespuesta;
import edu.unbosque.marketplace.pagos.PagoDtos.PagoSolicitud;
import edu.unbosque.marketplace.seguridad.FiltroAutenticacion;
import edu.unbosque.marketplace.seguridad.Sesion;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** S4 — POST /api/payments (HU-013). */
@RestController
@RequestMapping("/api/payments")
public class PagoController {

    private final PagoService servicio;

    public PagoController(PagoService servicio) {
        this.servicio = servicio;
    }

    @PostMapping
    public PagoRespuesta pagar(@RequestAttribute(FiltroAutenticacion.ATRIBUTO) Sesion sesion,
                               @RequestHeader("Idempotency-Key") String llave,
                               @Valid @RequestBody PagoSolicitud cuerpo) {
        return servicio.pagar(sesion, cuerpo, llave);
    }
}
