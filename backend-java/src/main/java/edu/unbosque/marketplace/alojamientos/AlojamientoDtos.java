package edu.unbosque.marketplace.alojamientos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * S2 — Cuerpos JSON de alojamientos (página 13, openapi.yaml).
 * Los identificadores son UUID, igual que en el resto del backend (V1 a V4).
 */
public final class AlojamientoDtos {

    private AlojamientoDtos() {
    }

    /** HU-005 y HU-006: crear o editar una publicación. */
    public record AlojamientoSolicitud(String titulo, String descripcion, String tipo, String ciudad, String direccion,
                                       Integer capacidad, BigDecimal precioPorNoche, List<String> servicios,
                                       String reglas, List<String> imagenes, UUID idPoliticaCancelacion) {
    }

    /** HU-023: política de cancelación disponible para el anfitrión. */
    public record PoliticaRespuesta(UUID id, String nombre, int diasAntelacion, int porcentajeDevolucion) {
    }

    /** HU-024: servicio adicional con costo (p. ej. Desayuno $30.000). */
    public record ServicioAdicionalSolicitud(String nombre, BigDecimal costo, Boolean activo) {
    }

    public record ServicioAdicionalRespuesta(UUID id, String nombre, BigDecimal costo, boolean activo) {
    }

    /** HU-024: tarifa de limpieza, descuento por estancia y servicios adicionales. */
    public record TarifasSolicitud(BigDecimal tarifaLimpieza, BigDecimal descuentoPct, Integer descuentoMinNoches,
                                   List<ServicioAdicionalSolicitud> serviciosAdicionales) {
    }

    /** HU-024 CA-04: tarifa de servicio del marketplace, configurable por el administrador (D-04). */
    public record TarifaServicio(BigDecimal porcentaje) {
    }

    /** Publicación completa: la ve el anfitrión dueño, el administrador y, si está ACTIVA, cualquier usuario. */
    public record AlojamientoRespuesta(UUID id, UUID idAnfitrion, String titulo, String descripcion, String tipo,
                                       String ciudad, String direccion, int capacidad, BigDecimal precioPorNoche,
                                       List<String> servicios, String reglas, List<String> imagenes, String estado,
                                       PoliticaRespuesta politicaCancelacion, BigDecimal tarifaLimpieza,
                                       BigDecimal descuentoPct, int descuentoMinNoches,
                                       List<ServicioAdicionalRespuesta> serviciosAdicionales,
                                       BigDecimal calificacionPromedio, int totalResenas, Instant fechaActualizacion) {
    }

    /** HU-007: activar o pausar. */
    public record EstadoSolicitud(String estado) {
    }

    /** HU-007 CA-08: cambio registrado con usuario, estado anterior, estado nuevo y fecha. */
    public record CambioEstadoRespuesta(UUID id, String estadoAnterior, String estadoNuevo, Instant fecha) {
    }

    /** HU-009: resultado de búsqueda con la información para comparar (CA-06). */
    public record ResultadoBusqueda(UUID id, String titulo, String imagen, String ciudad, String tipo, int capacidad,
                                    BigDecimal precioPorNoche) {
    }

    public record PaginaResultados(List<ResultadoBusqueda> items, int page, int size, long total) {
    }
}
