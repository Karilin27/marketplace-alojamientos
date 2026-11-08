package edu.unbosque.marketplace.identidad;

import edu.unbosque.marketplace.identidad.PerfilDtos.EstadoRespuesta;
import edu.unbosque.marketplace.identidad.PerfilDtos.EstadoSolicitud;
import edu.unbosque.marketplace.identidad.PerfilDtos.PerfilRespuesta;
import edu.unbosque.marketplace.identidad.PerfilDtos.PerfilSolicitud;
import edu.unbosque.marketplace.seguridad.FiltroAutenticacion;
import edu.unbosque.marketplace.seguridad.Sesion;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** S1 — GET/PUT /api/users/me y PATCH /api/users/me/status (HU-004). Requiere "Authorization: Bearer". */
@RestController
@RequestMapping("/api/users/me")
public class PerfilController {

    private final PerfilService servicio;

    public PerfilController(PerfilService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public PerfilRespuesta consultar(@RequestAttribute(FiltroAutenticacion.ATRIBUTO) Sesion sesion) {
        return servicio.obtener(sesion);
    }

    @PutMapping
    public PerfilRespuesta actualizar(@RequestAttribute(FiltroAutenticacion.ATRIBUTO) Sesion sesion,
                                      @RequestBody(required = false) PerfilSolicitud cuerpo) {
        return servicio.actualizar(sesion, cuerpo);
    }

    @PatchMapping("/status")
    public EstadoRespuesta cambiarEstado(@RequestAttribute(FiltroAutenticacion.ATRIBUTO) Sesion sesion,
                                         @RequestBody(required = false) EstadoSolicitud cuerpo) {
        return servicio.cambiarEstado(sesion, cuerpo);
    }
}
