package edu.unbosque.marketplace.administracion;

import edu.unbosque.marketplace.administracion.AdminDtos.CiudadDemanda;
import edu.unbosque.marketplace.administracion.AdminDtos.EventoAuditoria;
import edu.unbosque.marketplace.administracion.AdminDtos.PublicacionAdmin;
import edu.unbosque.marketplace.administracion.AdminDtos.ReservaRevision;
import edu.unbosque.marketplace.administracion.AdminDtos.Tabla;
import edu.unbosque.marketplace.administracion.AdminDtos.UsuarioAdmin;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

/**
 * S6 — Consultas de administración. Lee las tablas de los demás servicios en solo lectura (D-13) y escribe
 * solo en administracion.* y en los estados que el administrador cambia (cuentas y publicaciones).
 */
@Repository
public class AdminRepositorio {

    public record Cuenta(UUID id, String nombre, String correo, String rol, String estado) {
    }

    public record Publicacion(UUID id, UUID idAnfitrion, String titulo, String estado) {
    }

    private final JdbcTemplate jdbc;

    public AdminRepositorio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ================================================================== HU-003 recuperación (S1)

    public Optional<Cuenta> cuentaPorCorreo(String correo) {
        return jdbc.query("""
                SELECT id, nombre, correo, rol, estado FROM identidad.usuario WHERE lower(correo) = lower(?)""",
                (rs, n) -> new Cuenta(rs.getObject("id", UUID.class), rs.getString("nombre"), rs.getString("correo"),
                        rs.getString("rol"), rs.getString("estado")), correo).stream().findFirst();
    }

    /** Se guarda el hash SHA-256 del token, nunca el token (RNF de seguridad). */
    public void guardarToken(String hash, UUID idUsuario, Instant expira) {
        jdbc.update("UPDATE identidad.token_recuperacion SET usado = true WHERE id_usuario = ? AND NOT usado",
                idUsuario);
        jdbc.update("INSERT INTO identidad.token_recuperacion (valor, id_usuario, expira_en) VALUES (?, ?, ?)",
                hash, idUsuario, Timestamp.from(expira));
    }

    /** Token vigente y sin usar, bloqueado para que no se use dos veces a la vez. */
    public Optional<UUID> tokenValido(String hash, Instant ahora) {
        return jdbc.query("""
                SELECT id_usuario FROM identidad.token_recuperacion
                 WHERE valor = ? AND NOT usado AND expira_en > ? FOR UPDATE""",
                (rs, n) -> rs.getObject("id_usuario", UUID.class), hash, Timestamp.from(ahora)).stream().findFirst();
    }

    public void usarToken(String hash) {
        jdbc.update("UPDATE identidad.token_recuperacion SET usado = true WHERE valor = ?", hash);
    }

    public void cambiarContrasena(UUID idUsuario, String hash) {
        jdbc.update("UPDATE identidad.usuario SET contrasena_hash = ? WHERE id = ?", hash, idUsuario);
    }

    // ================================================================== HU-022 usuarios

    public List<UsuarioAdmin> usuarios(String buscar, String rol, String estado) {
        StringBuilder w = new StringBuilder(" WHERE true");
        List<Object> p = new ArrayList<>();
        if (buscar != null) {
            w.append(" AND (lower(nombre) LIKE lower(?) OR lower(correo) LIKE lower(?))");
            p.add("%" + buscar + "%");
            p.add("%" + buscar + "%");
        }
        if (rol != null) {
            w.append(" AND rol = ?");
            p.add(rol);
        }
        if (estado != null) {
            w.append(" AND estado = ?");
            p.add(estado);
        }
        return jdbc.query("""
                SELECT id, nombre, correo, rol, estado, motivo_restriccion, fecha_registro
                  FROM identidad.usuario""" + w + " ORDER BY fecha_registro DESC LIMIT 500",
                (rs, n) -> new UsuarioAdmin(rs.getObject("id", UUID.class), rs.getString("nombre"),
                        rs.getString("correo"), rs.getString("rol"), rs.getString("estado"),
                        rs.getString("motivo_restriccion"), instante(rs.getTimestamp("fecha_registro"))), p.toArray());
    }

    public Optional<Cuenta> cuentaBloqueando(UUID id) {
        return jdbc.query("SELECT id, nombre, correo, rol, estado FROM identidad.usuario WHERE id = ? FOR UPDATE",
                (rs, n) -> new Cuenta(rs.getObject("id", UUID.class), rs.getString("nombre"), rs.getString("correo"),
                        rs.getString("rol"), rs.getString("estado")), id).stream().findFirst();
    }

    public void cambiarEstadoCuenta(UUID id, String estado, String motivo) {
        jdbc.update("UPDATE identidad.usuario SET estado = ?, motivo_restriccion = ? WHERE id = ?",
                estado, "RESTRINGIDO".equals(estado) ? motivo : null, id);
    }

    // ================================================================== HU-018 moderación

    public List<PublicacionAdmin> publicaciones(String estado) {
        List<Object> p = new ArrayList<>();
        String w = "";
        if (estado != null) {
            w = " WHERE a.estado_publicacion = ?";
            p.add(estado);
        }
        return jdbc.query("""
                SELECT a.id, a.titulo, a.ciudad, a.tipo, a.estado_publicacion, u.nombre, u.correo, a.fecha_actualizacion,
                       (SELECT count(*) FROM reservas.reserva r WHERE r.id_alojamiento = a.id
                           AND r.estado = 'CONFIRMADA' AND r.fecha_llegada >= current_date) AS futuras
                  FROM catalogo.alojamiento a JOIN identidad.usuario u ON u.id = a.id_anfitrion""" + w
                        + " ORDER BY a.fecha_actualizacion DESC LIMIT 500",
                (rs, n) -> new PublicacionAdmin(rs.getObject("id", UUID.class), rs.getString("titulo"),
                        rs.getString("ciudad"), rs.getString("tipo"), rs.getString("estado_publicacion"),
                        rs.getString("nombre"), rs.getString("correo"), rs.getInt("futuras"),
                        instante(rs.getTimestamp("fecha_actualizacion"))), p.toArray());
    }

    public Optional<Publicacion> publicacionBloqueando(UUID id) {
        return jdbc.query("""
                SELECT id, id_anfitrion, titulo, estado_publicacion FROM catalogo.alojamiento WHERE id = ? FOR UPDATE""",
                (rs, n) -> new Publicacion(rs.getObject("id", UUID.class), rs.getObject("id_anfitrion", UUID.class),
                        rs.getString("titulo"), rs.getString("estado_publicacion")), id).stream().findFirst();
    }

    public void cambiarEstadoPublicacion(UUID id, String estado) {
        jdbc.update("UPDATE catalogo.alojamiento SET estado_publicacion = ?, fecha_actualizacion = now() WHERE id = ?",
                estado, id);
    }

    public Instant registrarModeracion(UUID idAdmin, UUID idAlojamiento, String accion, String motivo) {
        Timestamp t = jdbc.queryForObject("""
                INSERT INTO administracion.moderacion (id_admin, id_alojamiento, accion, motivo)
                VALUES (?, ?, ?, ?) RETURNING fecha""", Timestamp.class, idAdmin, idAlojamiento, accion, motivo);
        return instante(t);
    }

    public void registrarHistorialPublicacion(UUID id, String anterior, String nuevo, UUID actor, String motivo) {
        jdbc.update("""
                INSERT INTO catalogo.publicacion_historial (id_alojamiento, estado_anterior, estado_nuevo, id_actor, motivo)
                VALUES (?, ?, ?, ?, ?)""", id, anterior, nuevo, actor, motivo);
    }

    /** RN-19 / CU-17 A2: al bloquear, las reservas futuras se conservan y quedan marcadas para revisión manual. */
    public int marcarRevisionManual(UUID idAlojamiento) {
        return jdbc.update("""
                UPDATE reservas.reserva SET revision_manual = true
                 WHERE id_alojamiento = ? AND estado = 'CONFIRMADA' AND fecha_llegada >= current_date""", idAlojamiento);
    }

    public List<ReservaRevision> reservasEnRevision() {
        return jdbc.query("""
                SELECT r.id, a.titulo, u.nombre, r.fecha_llegada, r.fecha_salida, r.total, r.estado
                  FROM reservas.reserva r
                  JOIN catalogo.alojamiento a ON a.id = r.id_alojamiento
                  JOIN identidad.usuario u ON u.id = r.id_huesped
                 WHERE r.revision_manual ORDER BY r.fecha_llegada""",
                (rs, n) -> new ReservaRevision(rs.getObject("id", UUID.class), rs.getString("titulo"),
                        rs.getString("nombre"), String.valueOf(rs.getDate("fecha_llegada")),
                        String.valueOf(rs.getDate("fecha_salida")), rs.getBigDecimal("total"), rs.getString("estado")));
    }

    public int cerrarRevision(UUID idReserva) {
        return jdbc.update("UPDATE reservas.reserva SET revision_manual = false WHERE id = ? AND revision_manual",
                idReserva);
    }

    // ================================================================== HU-019 auditoría

    public List<EventoAuditoria> auditoria(UUID idUsuario, String entidad, String accion, LocalDate desde,
                                           LocalDate hasta, int pagina, int tamano, long[] total) {
        StringBuilder w = new StringBuilder(" WHERE true");
        List<Object> p = new ArrayList<>();
        if (idUsuario != null) {
            w.append(" AND x.id_usuario = ?");
            p.add(idUsuario);
        }
        if (entidad != null) {
            w.append(" AND lower(x.entidad) = lower(?)");
            p.add(entidad);
        }
        if (accion != null) {
            w.append(" AND x.accion ILIKE ?");
            p.add("%" + accion + "%");
        }
        if (desde != null) {
            w.append(" AND x.fecha >= ?");
            p.add(java.sql.Date.valueOf(desde));
        }
        if (hasta != null) {
            w.append(" AND x.fecha < ?");
            p.add(java.sql.Date.valueOf(hasta.plusDays(1)));
        }
        Long n = jdbc.queryForObject("SELECT count(*) FROM administracion.auditoria x" + w, Long.class, p.toArray());
        total[0] = n == null ? 0 : n;
        List<Object> pp = new ArrayList<>(p);
        pp.add(tamano);
        pp.add((long) pagina * tamano);
        return jdbc.query("""
                SELECT x.id, x.id_usuario, u.nombre, x.entidad, x.id_entidad, x.accion, x.resultado, x.fecha
                  FROM administracion.auditoria x LEFT JOIN identidad.usuario u ON u.id = x.id_usuario""" + w
                        + " ORDER BY x.fecha DESC LIMIT ? OFFSET ?",
                (rs, i) -> new EventoAuditoria(rs.getObject("id", UUID.class), rs.getObject("id_usuario", UUID.class),
                        rs.getString("nombre"), rs.getString("entidad"), rs.getString("id_entidad"),
                        rs.getString("accion"), rs.getString("resultado"), instante(rs.getTimestamp("fecha"))),
                pp.toArray());
    }

    // ================================================================== HU-020 dashboard

    public Map<String, Long> conteo(String sql) {
        Map<String, Long> m = new LinkedHashMap<>();
        jdbc.query(sql, (RowCallbackHandler) rs -> m.put(rs.getString(1), rs.getLong(2)));
        return m;
    }

    public long numero(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }

    /** D-16: ingresos estimados = suma de reservas CONFIRMADAS y COMPLETADAS. */
    public BigDecimal ingresosEstimados() {
        BigDecimal b = jdbc.queryForObject(
                "SELECT coalesce(sum(total), 0) FROM reservas.reserva WHERE estado IN ('CONFIRMADA', 'COMPLETADA')",
                BigDecimal.class);
        return b == null ? BigDecimal.ZERO : b;
    }

    public List<CiudadDemanda> ciudadesMayorDemanda(int limite) {
        return jdbc.query("""
                SELECT a.ciudad, count(*) AS reservas
                  FROM reservas.reserva r JOIN catalogo.alojamiento a ON a.id = r.id_alojamiento
                 WHERE r.estado IN ('CONFIRMADA', 'COMPLETADA')
                 GROUP BY a.ciudad ORDER BY reservas DESC, a.ciudad LIMIT ?""",
                (rs, n) -> new CiudadDemanda(rs.getString("ciudad"), rs.getLong("reservas")), limite);
    }

    // ================================================================== HU-021 reportes

    public Tabla tabla(String tipo) {
        return switch (tipo) {
            case "USUARIOS" -> consulta("Usuarios registrados", List.of("Nombre", "Correo", "Rol", "Estado", "Registro"), """
                    SELECT nombre, correo, rol, estado, to_char(fecha_registro, 'YYYY-MM-DD')
                      FROM identidad.usuario ORDER BY fecha_registro""");
            case "PUBLICACIONES" -> consulta("Publicaciones",
                    List.of("Título", "Ciudad", "Tipo", "Capacidad", "Precio por noche", "Estado", "Anfitrión"), """
                    SELECT a.titulo, a.ciudad, a.tipo, a.capacidad::text, a.precio_base::text, a.estado_publicacion, u.nombre
                      FROM catalogo.alojamiento a JOIN identidad.usuario u ON u.id = a.id_anfitrion ORDER BY a.titulo""");
            case "RESERVAS" -> consulta("Reservas",
                    List.of("Alojamiento", "Huésped", "Llegada", "Salida", "Huéspedes", "Total", "Estado"), """
                    SELECT a.titulo, u.nombre, r.fecha_llegada::text, r.fecha_salida::text, r.huespedes::text,
                           r.total::text, r.estado
                      FROM reservas.reserva r JOIN catalogo.alojamiento a ON a.id = r.id_alojamiento
                      JOIN identidad.usuario u ON u.id = r.id_huesped ORDER BY r.fecha_llegada DESC""");
            case "INGRESOS" -> consulta("Ingresos estimados por mes (reservas CONFIRMADAS y COMPLETADAS)",
                    List.of("Mes", "Reservas", "Ingresos"), """
                    SELECT to_char(date_trunc('month', fecha_llegada), 'YYYY-MM'), count(*)::text, sum(total)::text
                      FROM reservas.reserva WHERE estado IN ('CONFIRMADA', 'COMPLETADA')
                     GROUP BY 1 ORDER BY 1""");
            case "DEMANDA" -> consulta("Demanda por ciudad y tipo de alojamiento",
                    List.of("Ciudad", "Tipo", "Reservas"), """
                    SELECT a.ciudad, a.tipo, count(*)::text
                      FROM reservas.reserva r JOIN catalogo.alojamiento a ON a.id = r.id_alojamiento
                     WHERE r.estado IN ('CONFIRMADA', 'COMPLETADA')
                     GROUP BY a.ciudad, a.tipo ORDER BY count(*) DESC""");
            default -> throw new IllegalArgumentException(tipo);
        };
    }

    private Tabla consulta(String titulo, List<String> columnas, String sql) {
        List<List<String>> filas = jdbc.query(sql, (rs, n) -> {
            List<String> f = new ArrayList<>();
            for (int i = 1; i <= columnas.size(); i++) {
                String v = rs.getString(i);
                f.add(v == null ? "" : v);
            }
            return f;
        });
        return new Tabla(titulo, columnas, filas);
    }

    public void registrarReporte(UUID idAdmin, String tipo, String formato) {
        jdbc.update("INSERT INTO administracion.reporte_exportado (id_usuario, tipo, formato) VALUES (?, ?, ?)",
                idAdmin, tipo, formato);
    }

    private static Instant instante(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
