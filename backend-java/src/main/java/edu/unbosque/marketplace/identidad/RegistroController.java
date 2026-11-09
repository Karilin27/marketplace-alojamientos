package edu.unbosque.marketplace.identidad;

import edu.unbosque.marketplace.identidad.RegistroDtos.RegistroRespuesta;
import edu.unbosque.marketplace.identidad.RegistroDtos.RegistroSolicitud;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** S1 — POST /api/auth/register (HU-001). Ruta pública: no exige token. */
@RestController
@RequestMapping("/api/auth")
public class RegistroController {

    private final RegistroService servicio;

    public RegistroController(RegistroService servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistroRespuesta registrar(@RequestBody(required = false) RegistroSolicitud cuerpo) {
        return servicio.registrar(cuerpo);
    }
}
