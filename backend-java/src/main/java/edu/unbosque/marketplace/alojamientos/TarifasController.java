package edu.unbosque.marketplace.alojamientos;

import edu.unbosque.marketplace.alojamientos.AlojamientoDtos.PoliticaRespuesta;
import edu.unbosque.marketplace.alojamientos.AlojamientoDtos.TarifaServicio;
import edu.unbosque.marketplace.seguridad.FiltroAutenticacion;
import edu.unbosque.marketplace.seguridad.Sesion;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * S2 — GET /api/cancellation-policies (HU-023 CA-01, público) y
 * GET/PUT /api/admin/settings/service-fee (HU-024 CA-04, solo administrador).
 */
@RestController
public class TarifasController {

    private final TarifasService servicio;

    public TarifasController(TarifasService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/api/cancellation-policies")
    public List<PoliticaRespuesta> politicas() {
        return servicio.politicas();
    }

    @GetMapping("/api/admin/settings/service-fee")
    public TarifaServicio tarifaServicio(@RequestAttribute(FiltroAutenticacion.ATRIBUTO) Sesion sesion) {
        sesion.exigirRol(Sesion.Rol.ADMINISTRADOR);
        return servicio.tarifaServicio();
    }

    @PutMapping("/api/admin/settings/service-fee")
    public TarifaServicio cambiarTarifaServicio(@RequestAttribute(FiltroAutenticacion.ATRIBUTO) Sesion sesion,
                                                @RequestBody(required = false) TarifaServicio cuerpo) {
        return servicio.cambiarTarifaServicio(sesion, cuerpo);
    }
}
