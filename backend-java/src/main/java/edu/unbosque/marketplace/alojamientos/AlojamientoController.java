package edu.unbosque.marketplace.alojamientos;

import edu.unbosque.marketplace.alojamientos.AlojamientoDtos.AlojamientoRespuesta;
import edu.unbosque.marketplace.alojamientos.AlojamientoDtos.AlojamientoSolicitud;
import edu.unbosque.marketplace.alojamientos.AlojamientoDtos.CambioEstadoRespuesta;
import edu.unbosque.marketplace.alojamientos.AlojamientoDtos.EstadoSolicitud;
import edu.unbosque.marketplace.alojamientos.AlojamientoDtos.PaginaResultados;
import edu.unbosque.marketplace.alojamientos.AlojamientoDtos.TarifasSolicitud;
import edu.unbosque.marketplace.seguridad.FiltroAutenticacion;
import edu.unbosque.marketplace.seguridad.Sesion;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * S2 — /api/accommodations (página 13).
 * Públicos (sin token): GET /api/accommodations (HU-009) y GET /api/accommodations/{id} (HU-010).
 * Con token: crear (HU-005), editar (HU-006), mis publicaciones (HU-007 CA-09), estado (HU-007), tarifas (HU-024).
 */
@RestController
@RequestMapping("/api/accommodations")
public class AlojamientoController {

    private final PublicacionService publicaciones;
    private final EstadoPublicacionService estados;
    private final TarifasService tarifas;
    private final BusquedaService busqueda;

    public AlojamientoController(PublicacionService publicaciones, EstadoPublicacionService estados,
                                 TarifasService tarifas, BusquedaService busqueda) {
        this.publicaciones = publicaciones;
        this.estados = estados;
        this.tarifas = tarifas;
        this.busqueda = busqueda;
    }

    // ---------------------------------------------------------------- HU-005
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AlojamientoRespuesta crear(@RequestAttribute(FiltroAutenticacion.ATRIBUTO) Sesion sesion,
                                      @RequestBody(required = false) AlojamientoSolicitud cuerpo) {
        return publicaciones.crear(sesion, cuerpo);
    }

    // ---------------------------------------------------------------- HU-009 (público)
    @GetMapping
    public PaginaResultados buscar(@RequestParam(required = false) String ciudad,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate llegada,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate salida,
                                   @RequestParam(required = false) Integer huespedes,
                                   @RequestParam(required = false) String tipo,
                                   @RequestParam(required = false) BigDecimal precioMin,
                                   @RequestParam(required = false) BigDecimal precioMax,
                                   @RequestParam(required = false) List<String> servicios,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size) {
        return busqueda.buscar(ciudad, llegada, salida, huespedes, tipo, precioMin, precioMax, servicios, page, size);
    }

    // ---------------------------------------------------------------- HU-007 CA-09
    @GetMapping("/mine")
    public List<AlojamientoRespuesta> misPublicaciones(@RequestAttribute(FiltroAutenticacion.ATRIBUTO) Sesion sesion) {
        return publicaciones.misPublicaciones(sesion);
    }

    /** Detalle para el dueño (o el administrador) en cualquier estado, para editarla. */
    @GetMapping("/mine/{id}")
    public AlojamientoRespuesta miPublicacion(@RequestAttribute(FiltroAutenticacion.ATRIBUTO) Sesion sesion,
                                              @PathVariable UUID id) {
        return publicaciones.verPropia(sesion, id);
    }

    // ---------------------------------------------------------------- HU-010 (público)
    @GetMapping("/{id}")
    public AlojamientoRespuesta detalle(@PathVariable UUID id) {
        return busqueda.detalle(id);
    }

    // ---------------------------------------------------------------- HU-006
    @PutMapping("/{id}")
    public AlojamientoRespuesta editar(@RequestAttribute(FiltroAutenticacion.ATRIBUTO) Sesion sesion,
                                       @PathVariable UUID id,
                                       @RequestBody(required = false) AlojamientoSolicitud cuerpo) {
        return publicaciones.editar(sesion, id, cuerpo);
    }

    // ---------------------------------------------------------------- HU-007
    @PatchMapping("/{id}/status")
    public CambioEstadoRespuesta cambiarEstado(@RequestAttribute(FiltroAutenticacion.ATRIBUTO) Sesion sesion,
                                               @PathVariable UUID id,
                                               @RequestBody(required = false) EstadoSolicitud cuerpo) {
        return estados.cambiar(sesion, id, cuerpo);
    }

    // ---------------------------------------------------------------- HU-024
    @PutMapping("/{id}/pricing")
    public AlojamientoRespuesta configurarTarifas(@RequestAttribute(FiltroAutenticacion.ATRIBUTO) Sesion sesion,
                                                  @PathVariable UUID id,
                                                  @RequestBody(required = false) TarifasSolicitud cuerpo) {
        return tarifas.configurar(sesion, id, cuerpo);
    }
}
