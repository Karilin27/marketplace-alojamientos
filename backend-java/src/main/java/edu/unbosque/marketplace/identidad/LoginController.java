package edu.unbosque.marketplace.identidad;

import edu.unbosque.marketplace.identidad.LoginDtos.LoginRespuesta;
import edu.unbosque.marketplace.identidad.LoginDtos.LoginSolicitud;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** S1 — POST /api/auth/login (HU-002). Ruta pública: no exige token. */
@RestController
@RequestMapping("/api/auth")
public class LoginController {

    private final LoginService servicio;

    public LoginController(LoginService servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/login")
    public LoginRespuesta iniciarSesion(@RequestBody(required = false) LoginSolicitud cuerpo) {
        return servicio.iniciarSesion(cuerpo);
    }
}
