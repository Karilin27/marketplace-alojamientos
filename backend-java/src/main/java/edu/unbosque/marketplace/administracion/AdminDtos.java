package edu.unbosque.marketplace.administracion;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** S6 — Cuerpos JSON de administración (página 13: /api/admin/...) y de recuperación de acceso (S1). */
public final class AdminDtos {

    private AdminDtos() {
    }

    // HU-003
    public record RecuperacionSolicitud(String correo) {
    }

    /** Mensaje siempre igual (CA-02); el token se muestra solo porque la recuperación es simulada (A-04). */
    public record RecuperacionRespuesta(String mensaje, String tokenSimulado, Instant expiraEn) {
    }

    public record RestablecerSolicitud(String token, String nuevaContrasena) {
    }

    // HU-022
    public record UsuarioAdmin(UUID id, String nombre, String correo, String rol, String estado,
                               String motivoRestriccion, Instant fechaRegistro) {
    }

    public record EstadoUsuarioSolicitud(String estado, String motivo) {
    }

    // HU-018
    public record PublicacionAdmin(UUID id, String titulo, String ciudad, String tipo, String estado, String anfitrion,
                                   String correoAnfitrion, int reservasFuturas, Instant fechaActualizacion) {
    }

    public record ModeracionSolicitud(String accion, String motivo) {
    }

    public record ModeracionRespuesta(UUID id, String estadoAnterior, String estadoNuevo, int reservasEnRevision,
                                      Instant fecha) {
    }

    public record ReservaRevision(UUID id, String alojamiento, String huesped, String fechaLlegada,
                                  String fechaSalida, BigDecimal total, String estado) {
    }

    public record DecisionRevisionSolicitud(String decision) {
    }

    // HU-019
    public record EventoAuditoria(UUID id, UUID idUsuario, String usuario, String entidad, String idEntidad,
                                  String accion, String resultado, Instant fecha) {
    }

    public record PaginaAuditoria(List<EventoAuditoria> items, int page, int size, long total) {
    }

    // HU-020
    public record Dashboard(long usuarios, Map<String, Long> usuariosPorRol, long publicacionesActivas,
                            Map<String, Long> reservasPorEstado, BigDecimal ingresosEstimados,
                            List<CiudadDemanda> ciudadesMayorDemanda, Instant calculadoEn) {
    }

    public record CiudadDemanda(String ciudad, long reservas) {
    }

    // HU-021
    public record ReporteSolicitud(String tipo, String formato) {
    }

    /** Datos tabulares de un reporte, antes de convertirlos a PDF o Excel. */
    public record Tabla(String titulo, List<String> columnas, List<List<String>> filas) {
    }
}
