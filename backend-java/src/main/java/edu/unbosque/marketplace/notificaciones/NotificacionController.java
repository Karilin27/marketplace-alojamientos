package edu.unbosque.marketplace.notificaciones;

import edu.unbosque.marketplace.reservas.web.Dtos.Pagina;
import edu.unbosque.marketplace.seguridad.FiltroAutenticacion;
import edu.unbosque.marketplace.seguridad.Sesion;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** S5 — GET /api/notifications y PATCH /api/notifications/{id}/read (HU-016). */
@RestController
@RequestMapping("/api/notifications")
public class NotificacionController {

    private final NotificacionService servicio;

    public NotificacionController(NotificacionService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public Pagina<Notificacion> listar(@RequestAttribute(FiltroAutenticacion.ATRIBUTO) Sesion sesion,
                                       @RequestParam(defaultValue = "false") boolean unread,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return servicio.listar(sesion.id(), unread, page, size);
    }

    @PatchMapping("/{id}/read")
    public Notificacion marcarLeida(@RequestAttribute(FiltroAutenticacion.ATRIBUTO) Sesion sesion,
                                    @PathVariable UUID id) {
        return servicio.marcarLeida(id, sesion.id());
    }
}
