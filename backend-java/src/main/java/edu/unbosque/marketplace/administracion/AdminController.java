package edu.unbosque.marketplace.administracion;

import edu.unbosque.marketplace.administracion.AdminDtos.Dashboard;
import edu.unbosque.marketplace.administracion.AdminDtos.DecisionRevisionSolicitud;
import edu.unbosque.marketplace.administracion.AdminDtos.EstadoUsuarioSolicitud;
import edu.unbosque.marketplace.administracion.AdminDtos.ModeracionRespuesta;
import edu.unbosque.marketplace.administracion.AdminDtos.ModeracionSolicitud;
import edu.unbosque.marketplace.administracion.AdminDtos.PaginaAuditoria;
import edu.unbosque.marketplace.administracion.AdminDtos.PublicacionAdmin;
import edu.unbosque.marketplace.administracion.AdminDtos.RecuperacionRespuesta;
import edu.unbosque.marketplace.administracion.AdminDtos.RecuperacionSolicitud;
import edu.unbosque.marketplace.administracion.AdminDtos.ReporteSolicitud;
import edu.unbosque.marketplace.administracion.AdminDtos.ReservaRevision;
import edu.unbosque.marketplace.administracion.AdminDtos.RestablecerSolicitud;
import edu.unbosque.marketplace.administracion.AdminDtos.UsuarioAdmin;
import edu.unbosque.marketplace.seguridad.FiltroAutenticacion;
import edu.unbosque.marketplace.seguridad.Sesion;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * S6 y S1 — Rutas de administración (solo ADMINISTRADOR) y de recuperación de acceso (públicas).
 * HU-003: POST /api/auth/password-recovery · POST /api/auth/password-reset
 * HU-022: GET /api/admin/users · PATCH /api/admin/users/{id}/status
 * HU-018: GET /api/admin/accommodations · PATCH /api/admin/accommodations/{id}/moderation ·
 *         GET /api/admin/bookings/manual-review · PATCH /api/admin/bookings/{id}/manual-review
 * HU-019: GET /api/admin/audit · HU-020: GET /api/admin/dashboard · HU-021: POST /api/admin/reports/export
 */
@RestController
public class AdminController {

    private final AdminService admin;
    private final RecuperacionService recuperacion;
    private final ReporteService reportes;

    public AdminController(AdminService admin, RecuperacionService recuperacion, ReporteService reportes) {
        this.admin = admin;
        this.recuperacion = recuperacion;
        this.reportes = reportes;
    }

    // ------------------------------------------------------------------ HU-003 (públicas)
    @PostMapping("/api/auth/password-recovery")
    public RecuperacionRespuesta solicitarRecuperacion(@RequestBody(required = false) RecuperacionSolicitud cuerpo) {
        return recuperacion.solicitar(cuerpo);
    }

    @PostMapping("/api/auth/password-reset")
    public Map<String, String> restablecer(@RequestBody(required = false) RestablecerSolicitud cuerpo) {
        return recuperacion.restablecer(cuerpo);
    }

    // ------------------------------------------------------------------ HU-022
    @GetMapping("/api/admin/users")
    public List<UsuarioAdmin> usuarios(@RequestAttribute(FiltroAutenticacion.ATRIBUTO) Sesion sesion,
                                       @RequestParam(required = false) String buscar,
                                       @RequestParam(required = false) String rol,
                                       @RequestParam(required = false) String estado) {
        return admin.usuarios(sesion, buscar, rol, estado);
    }

    @PatchMapping("/api/admin/users/{id}/status")
    public UsuarioAdmin cambiarEstadoUsuario(@RequestAttribute(FiltroAutenticacion.ATRIBUTO) Sesion sesion,
                                             @PathVariable UUID id,
                                             @RequestBody(required = false) EstadoUsuarioSolicitud cuerpo) {
        return admin.cambiarEstadoUsuario(sesion, id, cuerpo);
    }

    // ------------------------------------------------------------------ HU-018
    @GetMapping("/api/admin/accommodations")
    public List<PublicacionAdmin> publicaciones(@RequestAttribute(FiltroAutenticacion.ATRIBUTO) Sesion sesion,
                                                @RequestParam(required = false) String estado) {
        return admin.publicaciones(sesion, estado);
    }

    @PatchMapping("/api/admin/accommodations/{id}/moderation")
    public ModeracionRespuesta moderar(@RequestAttribute(FiltroAutenticacion.ATRIBUTO) Sesion sesion,
                                       @PathVariable UUID id,
                                       @RequestBody(required = false) ModeracionSolicitud cuerpo) {
        return admin.moderar(sesion, id, cuerpo);
    }

    @GetMapping("/api/admin/bookings/manual-review")
    public List<ReservaRevision> reservasEnRevision(@RequestAttribute(FiltroAutenticacion.ATRIBUTO) Sesion sesion) {
        return admin.reservasEnRevision(sesion);
    }

    @PatchMapping("/api/admin/bookings/{id}/manual-review")
    public Map<String, String> cerrarRevision(@RequestAttribute(FiltroAutenticacion.ATRIBUTO) Sesion sesion,
                                              @PathVariable UUID id,
                                              @RequestBody(required = false) DecisionRevisionSolicitud cuerpo) {
        return admin.cerrarRevision(sesion, id, cuerpo);
    }

    // ------------------------------------------------------------------ HU-019
    @GetMapping("/api/admin/audit")
    public PaginaAuditoria auditoria(@RequestAttribute(FiltroAutenticacion.ATRIBUTO) Sesion sesion,
                                     @RequestParam(required = false) UUID usuario,
                                     @RequestParam(required = false) String entidad,
                                     @RequestParam(required = false) String accion,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "50") int size) {
        return admin.auditoria(sesion, usuario, entidad, accion, desde, hasta, page, size);
    }

    // ------------------------------------------------------------------ HU-020
    @GetMapping("/api/admin/dashboard")
    public Dashboard dashboard(@RequestAttribute(FiltroAutenticacion.ATRIBUTO) Sesion sesion) {
        return admin.dashboard(sesion);
    }

    // ------------------------------------------------------------------ HU-021
    @PostMapping("/api/admin/reports/export")
    public ResponseEntity<byte[]> exportar(@RequestAttribute(FiltroAutenticacion.ATRIBUTO) Sesion sesion,
                                           @RequestBody(required = false) ReporteSolicitud cuerpo) {
        ReporteService.Archivo a = reportes.exportar(sesion, cuerpo);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(a.tipoMime()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + a.nombre() + "\"")
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .body(a.contenido());
    }
}
