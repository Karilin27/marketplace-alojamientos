package edu.unbosque.marketplace.alojamientos;

import edu.unbosque.marketplace.alojamientos.AlojamientoDtos.PoliticaRespuesta;
import edu.unbosque.marketplace.alojamientos.AlojamientoDtos.ResultadoBusqueda;
import edu.unbosque.marketplace.alojamientos.AlojamientoDtos.ServicioAdicionalRespuesta;
import edu.unbosque.marketplace.alojamientos.ReglasAlojamiento.Adicional;
import edu.unbosque.marketplace.alojamientos.ReglasAlojamiento.Datos;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** S2 — Acceso a datos del esquema catalogo (V2 y V5). Solo S2 escribe en estas tablas. */
@Repository
public class AlojamientoRepositorio {

    /** Fila de catalogo.alojamiento con su política. */
    public record Registro(UUID id, UUID idAnfitrion, String titulo, String descripcion, String tipo, String ciudad,
                           String direccion, Integer capacidad, BigDecimal precio, String reglas, String estado,
                           UUID idPolitica, BigDecimal tarifaLimpieza, BigDecimal descuentoPct,
                           int descuentoMinNoches, Instant fechaActualizacion) {
    }

    /** Filtros de HU-009. Los nulos no filtran. */
    public record Filtros(String ciudad, LocalDate llegada, LocalDate salida, Integer huespedes, String tipo,
                          BigDecimal precioMin, BigDecimal precioMax, List<String> servicios) {
    }

    private static final String SELECT_ALOJAMIENTO = """
            SELECT a.id, a.id_anfitrion, a.titulo, a.descripcion, a.tipo, a.ciudad, a.direccion, a.capacidad,
                   a.precio_base, a.reglas, a.estado_publicacion, a.id_politica, a.tarifa_limpieza,
                   a.descuento_pct, a.descuento_min_noches, a.fecha_actualizacion
              FROM catalogo.alojamiento a""";

    private final JdbcTemplate jdbc;

    public AlojamientoRepositorio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------------------------------------------------------------- políticas (HU-023)

    public List<PoliticaRespuesta> politicas() {
        return jdbc.query("""
                SELECT id, nombre, dias_antelacion, porcentaje_reembolso
                  FROM catalogo.politica_cancelacion ORDER BY dias_antelacion""", (rs, n) -> politica(rs));
    }

    public Optional<PoliticaRespuesta> politica(UUID id) {
        if (id == null) return Optional.empty();
        return jdbc.query("""
                SELECT id, nombre, dias_antelacion, porcentaje_reembolso
                  FROM catalogo.politica_cancelacion WHERE id = ?""", (rs, n) -> politica(rs), id)
                .stream().findFirst();
    }

    private static PoliticaRespuesta politica(ResultSet rs) throws SQLException {
        return new PoliticaRespuesta(rs.getObject("id", UUID.class), rs.getString("nombre"),
                rs.getInt("dias_antelacion"), rs.getInt("porcentaje_reembolso"));
    }

    // ---------------------------------------------------------------- servicios (amenities)

    /** Nombres de los servicios del catálogo (WiFi, Cocina…), para validar lo que envía el anfitrión. */
    public List<String> serviciosDisponibles() {
        return jdbc.queryForList("SELECT nombre FROM catalogo.servicio ORDER BY nombre", String.class);
    }

    // ---------------------------------------------------------------- publicación (HU-005, HU-006)

    public UUID insertar(UUID idAnfitrion, Datos d) {
        return jdbc.queryForObject("""
                INSERT INTO catalogo.alojamiento (id_anfitrion, id_politica, titulo, descripcion, tipo, ciudad,
                       direccion, capacidad, precio_base, reglas, estado_publicacion)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'BORRADOR')
                RETURNING id""", UUID.class,
                idAnfitrion, d.idPolitica(), d.titulo(), d.descripcion(), d.tipo(), d.ciudad(), d.direccion(),
                d.capacidad(), d.precio(), d.reglas());
    }

    public void actualizar(UUID id, Datos d) {
        jdbc.update("""
                UPDATE catalogo.alojamiento
                   SET titulo = ?, descripcion = ?, tipo = ?, ciudad = ?, direccion = ?, capacidad = ?,
                       precio_base = ?, reglas = ?, id_politica = ?, fecha_actualizacion = now()
                 WHERE id = ?""",
                d.titulo(), d.descripcion(), d.tipo(), d.ciudad(), d.direccion(), d.capacidad(), d.precio(),
                d.reglas(), d.idPolitica(), id);
    }

    /** Reemplaza los servicios (amenities) por los nombres dados; los nombres ya se validaron. */
    public void reemplazarServicios(UUID id, List<String> nombres) {
        jdbc.update("DELETE FROM catalogo.alojamiento_servicio WHERE id_alojamiento = ?", id);
        for (String nombre : nombres) {
            jdbc.update("""
                    INSERT INTO catalogo.alojamiento_servicio (id_alojamiento, id_servicio)
                    SELECT ?, s.id FROM catalogo.servicio s WHERE lower(s.nombre) = lower(?)
                    ON CONFLICT DO NOTHING""", id, nombre);
        }
    }

    public void reemplazarFotos(UUID id, List<String> urls) {
        jdbc.update("DELETE FROM catalogo.foto_alojamiento WHERE id_alojamiento = ?", id);
        for (int i = 0; i < urls.size(); i++) {
            jdbc.update("INSERT INTO catalogo.foto_alojamiento (id_alojamiento, url, orden) VALUES (?, ?, ?)",
                    id, urls.get(i), i + 1);
        }
    }

    public Optional<Registro> buscar(UUID id) {
        return jdbc.query(SELECT_ALOJAMIENTO + " WHERE a.id = ?", (rs, n) -> registro(rs), id).stream().findFirst();
    }

    /** Bloquea la fila hasta el fin de la transacción: evita dos ediciones o cambios de estado simultáneos. */
    public Optional<Registro> buscarBloqueando(UUID id) {
        return jdbc.query(SELECT_ALOJAMIENTO + " WHERE a.id = ? FOR UPDATE", (rs, n) -> registro(rs), id)
                .stream().findFirst();
    }

    /** HU-007 CA-09: publicaciones del anfitrión, de la más reciente a la más antigua. */
    public List<Registro> delAnfitrion(UUID idAnfitrion) {
        return jdbc.query(SELECT_ALOJAMIENTO + " WHERE a.id_anfitrion = ? ORDER BY a.fecha_actualizacion DESC",
                (rs, n) -> registro(rs), idAnfitrion);
    }

    public List<String> servicios(UUID id) {
        return jdbc.queryForList("""
                SELECT s.nombre FROM catalogo.alojamiento_servicio x
                  JOIN catalogo.servicio s ON s.id = x.id_servicio
                 WHERE x.id_alojamiento = ? ORDER BY s.nombre""", String.class, id);
    }

    public List<String> fotos(UUID id) {
        return jdbc.queryForList(
                "SELECT url FROM catalogo.foto_alojamiento WHERE id_alojamiento = ? ORDER BY orden", String.class, id);
    }

    // ---------------------------------------------------------------- estado (HU-007)

    public void cambiarEstado(UUID id, String estado) {
        jdbc.update("""
                UPDATE catalogo.alojamiento SET estado_publicacion = ?, fecha_actualizacion = now()
                 WHERE id = ?""", estado, id);
    }

    /** HU-007 CA-08: usuario, estado anterior, estado nuevo y fecha. */
    public Instant registrarCambioEstado(UUID id, String anterior, String nuevo, UUID actor, String motivo) {
        Timestamp t = jdbc.queryForObject("""
                INSERT INTO catalogo.publicacion_historial (id_alojamiento, estado_anterior, estado_nuevo, id_actor, motivo)
                VALUES (?, ?, ?, ?, ?) RETURNING fecha""", Timestamp.class, id, anterior, nuevo, actor, motivo);
        return t == null ? Instant.now() : t.toInstant();
    }

    /** Administradores activos, para avisarles de una publicación en PENDIENTE_REVISION (HU-006 CA-06). */
    public List<UUID> administradoresActivos() {
        return jdbc.queryForList(
                "SELECT id FROM identidad.usuario WHERE rol = 'ADMINISTRADOR' AND estado = 'ACTIVO'", UUID.class);
    }

    // ---------------------------------------------------------------- tarifas (HU-024)

    public void actualizarTarifas(UUID id, BigDecimal limpieza, BigDecimal descuentoPct, int minNoches) {
        jdbc.update("""
                UPDATE catalogo.alojamiento
                   SET tarifa_limpieza = ?, descuento_pct = ?, descuento_min_noches = ?, fecha_actualizacion = now()
                 WHERE id = ?""", limpieza, descuentoPct, minNoches, id);
    }

    /**
     * Deja exactamente la lista enviada como servicios adicionales del alojamiento. Los que ya no vienen se
     * desactivan (no se borran: una reserva pasada pudo incluirlos; S3 copia nombre y costo al reservar).
     */
    public void sincronizarAdicionales(UUID id, List<Adicional> lista) {
        jdbc.update("UPDATE catalogo.servicio_adicional SET activo = false WHERE id_alojamiento = ?", id);
        for (Adicional a : lista) {
            int n = jdbc.update("""
                    UPDATE catalogo.servicio_adicional SET costo = ?, activo = ?, nombre = ?
                     WHERE id_alojamiento = ? AND lower(nombre) = lower(?)""",
                    a.costo(), a.activo(), a.nombre(), id, a.nombre());
            if (n == 0) {
                jdbc.update("""
                        INSERT INTO catalogo.servicio_adicional (id_alojamiento, nombre, costo, activo)
                        VALUES (?, ?, ?, ?)""", id, a.nombre(), a.costo(), a.activo());
            }
        }
    }

    public List<ServicioAdicionalRespuesta> adicionales(UUID id, boolean soloActivos) {
        return jdbc.query("""
                SELECT id, nombre, costo, activo FROM catalogo.servicio_adicional
                 WHERE id_alojamiento = ?""" + (soloActivos ? " AND activo" : "") + " ORDER BY nombre",
                (rs, n) -> new ServicioAdicionalRespuesta(rs.getObject("id", UUID.class), rs.getString("nombre"),
                        rs.getBigDecimal("costo"), rs.getBoolean("activo")), id);
    }

    public BigDecimal tarifaServicioPct() {
        List<String> v = jdbc.queryForList(
                "SELECT valor FROM catalogo.parametro_sistema WHERE clave = 'tarifa_servicio_pct'", String.class);
        return v.isEmpty() ? BigDecimal.TEN : new BigDecimal(v.get(0));
    }

    public void guardarTarifaServicioPct(BigDecimal pct) {
        jdbc.update("""
                INSERT INTO catalogo.parametro_sistema (clave, valor) VALUES ('tarifa_servicio_pct', ?)
                ON CONFLICT (clave) DO UPDATE SET valor = EXCLUDED.valor""", pct.stripTrailingZeros().toPlainString());
    }

    // ---------------------------------------------------------------- reseñas (para el detalle, HU-010)

    /** Promedio y cantidad de reseñas visibles (RN-20: las ocultas no cuentan). */
    public BigDecimal[] calificacion(UUID id) {
        return jdbc.queryForObject("""
                SELECT avg(calificacion)::numeric AS promedio, count(*) AS total
                  FROM experiencia.resena WHERE id_alojamiento = ? AND visible""",
                (rs, n) -> new BigDecimal[] {
                        rs.getBigDecimal("promedio") == null ? null
                                : rs.getBigDecimal("promedio").setScale(1, RoundingMode.HALF_UP),
                        BigDecimal.valueOf(rs.getLong("total")) }, id);
    }

    // ---------------------------------------------------------------- búsqueda (HU-009)

    /**
     * Publicaciones ACTIVAS que cumplen los filtros (CA-01, CA-02, CA-03). Con fechas, se excluyen las que tienen
     * en ese rango una reserva vigente (PENDIENTE_PAGO o CONFIRMADA) o un periodo NO_DISPONIBLE (RN-04, RN-05).
     * La consulta se arma con parámetros (?), nunca concatenando valores del usuario.
     */
    public List<ResultadoBusqueda> buscar(Filtros f, int pagina, int tamano, long[] total) {
        StringBuilder where = new StringBuilder(" WHERE a.estado_publicacion = 'ACTIVA'");
        List<Object> p = new ArrayList<>();
        if (f.ciudad() != null) {
            where.append(" AND lower(a.ciudad) = lower(?)");
            p.add(f.ciudad());
        }
        if (f.tipo() != null) {
            where.append(" AND a.tipo = ?");
            p.add(f.tipo());
        }
        if (f.huespedes() != null) {
            where.append(" AND a.capacidad >= ?");
            p.add(f.huespedes());
        }
        if (f.precioMin() != null) {
            where.append(" AND a.precio_base >= ?");
            p.add(f.precioMin());
        }
        if (f.precioMax() != null) {
            where.append(" AND a.precio_base <= ?");
            p.add(f.precioMax());
        }
        for (String servicio : f.servicios()) {
            where.append("""
                     AND EXISTS (SELECT 1 FROM catalogo.alojamiento_servicio x
                                   JOIN catalogo.servicio s ON s.id = x.id_servicio
                                  WHERE x.id_alojamiento = a.id AND lower(s.nombre) = lower(?))""");
            p.add(servicio.toLowerCase(Locale.ROOT));
        }
        if (f.llegada() != null && f.salida() != null) {
            where.append("""
                     AND NOT EXISTS (SELECT 1 FROM reservas.reserva r
                                      WHERE r.id_alojamiento = a.id
                                        AND r.estado IN ('PENDIENTE_PAGO', 'CONFIRMADA')
                                        AND daterange(r.fecha_llegada, r.fecha_salida, '[)') && daterange(?, ?, '[)'))
                     AND NOT EXISTS (SELECT 1 FROM reservas.disponibilidad d
                                      WHERE d.id_alojamiento = a.id AND d.tipo = 'NO_DISPONIBLE'
                                        AND daterange(d.fecha_inicio, d.fecha_fin, '[)') && daterange(?, ?, '[)'))""");
            p.add(f.llegada());
            p.add(f.salida());
            p.add(f.llegada());
            p.add(f.salida());
        }
        Long n = jdbc.queryForObject("SELECT count(*) FROM catalogo.alojamiento a" + where, Long.class, p.toArray());
        total[0] = n == null ? 0 : n;
        List<Object> pp = new ArrayList<>(p);
        pp.add(tamano);
        pp.add((long) pagina * tamano);
        return jdbc.query("""
                SELECT a.id, a.titulo, a.ciudad, a.tipo, a.capacidad, a.precio_base,
                       (SELECT f.url FROM catalogo.foto_alojamiento f
                         WHERE f.id_alojamiento = a.id ORDER BY f.orden LIMIT 1) AS imagen
                  FROM catalogo.alojamiento a""" + where + " ORDER BY a.precio_base, a.titulo LIMIT ? OFFSET ?",
                (rs, i) -> new ResultadoBusqueda(rs.getObject("id", UUID.class), rs.getString("titulo"),
                        rs.getString("imagen"), rs.getString("ciudad"), rs.getString("tipo"), rs.getInt("capacidad"),
                        rs.getBigDecimal("precio_base")), pp.toArray());
    }

    private static Registro registro(ResultSet rs) throws SQLException {
        Timestamp t = rs.getTimestamp("fecha_actualizacion");
        return new Registro(rs.getObject("id", UUID.class), rs.getObject("id_anfitrion", UUID.class),
                rs.getString("titulo"), rs.getString("descripcion"), rs.getString("tipo"), rs.getString("ciudad"),
                rs.getString("direccion"), (Integer) rs.getObject("capacidad"), rs.getBigDecimal("precio_base"),
                rs.getString("reglas"), rs.getString("estado_publicacion"), rs.getObject("id_politica", UUID.class),
                rs.getBigDecimal("tarifa_limpieza"), rs.getBigDecimal("descuento_pct"),
                rs.getInt("descuento_min_noches"), t == null ? null : t.toInstant());
    }
}
