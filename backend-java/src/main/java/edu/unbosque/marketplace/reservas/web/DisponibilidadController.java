package edu.unbosque.marketplace.reservas.web;

import edu.unbosque.marketplace.reservas.DisponibilidadService;
import edu.unbosque.marketplace.reservas.domain.Periodo;
import edu.unbosque.marketplace.reservas.web.Dtos.DisponibilidadRespuesta;
import edu.unbosque.marketplace.reservas.web.Dtos.DisponibilidadSolicitud;
import edu.unbosque.marketplace.seguridad.FiltroAutenticacion;
import edu.unbosque.marketplace.seguridad.Sesion;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** S3 — GET (público) y PUT (anfitrión propietario) /api/availability/{accommodationId} (HU-008). */
@RestController
@RequestMapping("/api/availability")
public class DisponibilidadController {

    private final DisponibilidadService servicio;

    public DisponibilidadController(DisponibilidadService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/{accommodationId}")
    public DisponibilidadRespuesta consultar(@PathVariable UUID accommodationId,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return servicio.consultar(accommodationId, desde, hasta);
    }

    @PutMapping("/{accommodationId}")
    public DisponibilidadRespuesta reemplazar(@RequestAttribute(FiltroAutenticacion.ATRIBUTO) Sesion sesion,
                                              @PathVariable UUID accommodationId,
                                              @Valid @RequestBody DisponibilidadSolicitud cuerpo) {
        return servicio.reemplazar(sesion, accommodationId, cuerpo.periodos().stream()
                .map(p -> new Periodo(p.fechaInicio(), p.fechaFin(), p.tipo())).toList());
    }
}
