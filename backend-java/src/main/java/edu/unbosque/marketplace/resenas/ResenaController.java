package edu.unbosque.marketplace.resenas;

import edu.unbosque.marketplace.resenas.ResenaDtos.ResenaAdmin;
import edu.unbosque.marketplace.resenas.ResenaDtos.ResenaSolicitud;
import edu.unbosque.marketplace.resenas.ResenaDtos.ResenasAlojamiento;
import edu.unbosque.marketplace.resenas.ResenaDtos.VisibilidadSolicitud;
import edu.unbosque.marketplace.seguridad.FiltroAutenticacion;
import edu.unbosque.marketplace.seguridad.Sesion;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * S5 — Reseñas (página 13).
 * POST /api/reviews (huésped, HU-017) · GET /api/reviews?accommodationId= (público, HU-017 CA-06)
 * GET /api/admin/reviews[?accommodationId=] y PATCH /api/admin/reviews/{id}/visibility (administrador, HU-025).
 */
@RestController
public class ResenaController {

    private final ResenaService servicio;

    public ResenaController(ResenaService servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/api/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ResenasAlojamiento registrar(@RequestAttribute(FiltroAutenticacion.ATRIBUTO) Sesion sesion,
                                        @RequestBody(required = false) ResenaSolicitud cuerpo) {
        return servicio.registrar(sesion, cuerpo);
    }

    @GetMapping("/api/reviews")
    public ResenasAlojamiento deAlojamiento(@RequestParam(required = false) UUID accommodationId) {
        return servicio.deAlojamiento(accommodationId);
    }

    @GetMapping("/api/admin/reviews")
    public List<ResenaAdmin> paraAdministrador(@RequestAttribute(FiltroAutenticacion.ATRIBUTO) Sesion sesion,
                                               @RequestParam(required = false) UUID accommodationId) {
        return servicio.paraAdministrador(sesion, accommodationId);
    }

    @PatchMapping("/api/admin/reviews/{id}/visibility")
    public ResenasAlojamiento cambiarVisibilidad(@RequestAttribute(FiltroAutenticacion.ATRIBUTO) Sesion sesion,
                                                 @PathVariable UUID id,
                                                 @RequestBody(required = false) VisibilidadSolicitud cuerpo) {
        return servicio.cambiarVisibilidad(sesion, id, cuerpo);
    }
}
