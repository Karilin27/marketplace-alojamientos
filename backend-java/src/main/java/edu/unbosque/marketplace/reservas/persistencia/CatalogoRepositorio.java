package edu.unbosque.marketplace.reservas.persistencia;

import edu.unbosque.marketplace.reservas.domain.DatosTarifa;
import edu.unbosque.marketplace.reservas.domain.PoliticaAplicada;
import edu.unbosque.marketplace.reservas.domain.ServicioAdicional;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Lectura de datos de S2 (catálogo) a través de las vistas reservas.v_* definidas en
 * db/migrations/V3_1__vistas_catalogo_s2.sql. Si los nombres reales de S2 son distintos, solo se ajustan las vistas.
 */
@Repository
public class CatalogoRepositorio {

    public record Alojamiento(UUID id, UUID idAnfitrion, boolean publicado, DatosTarifa tarifa,
                              PoliticaAplicada politica) {
    }

    private final JdbcTemplate jdbc;

    public CatalogoRepositorio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Alojamiento> buscar(UUID id, BigDecimal tarifaServicioPct) {
        return jdbc.query("""
                SELECT id, id_anfitrion, estado_publicacion, precio_base, tarifa_limpieza, descuento_pct,
                       descuento_min_noches, capacidad, politica_nombre, politica_dias, politica_pct
                  FROM reservas.v_alojamiento WHERE id = ?""", (rs, n) -> new Alojamiento(
                rs.getObject("id", UUID.class),
                rs.getObject("id_anfitrion", UUID.class),
                "PUBLICADO".equals(rs.getString("estado_publicacion")),
                new DatosTarifa(rs.getBigDecimal("precio_base"), rs.getBigDecimal("tarifa_limpieza"),
                        rs.getBigDecimal("descuento_pct"), rs.getInt("descuento_min_noches"), tarifaServicioPct,
                        rs.getInt("capacidad")),
                new PoliticaAplicada(rs.getString("politica_nombre"), rs.getInt("politica_dias"),
                        rs.getInt("politica_pct"))), id).stream().findFirst();
    }

    public List<ServicioAdicional> serviciosActivos(UUID idAlojamiento) {
        return jdbc.query("""
                SELECT id, nombre, costo FROM reservas.v_servicio_adicional
                 WHERE id_alojamiento = ? AND activo""",
                (rs, n) -> new ServicioAdicional(rs.getObject("id", UUID.class).toString(), rs.getString("nombre"),
                        rs.getBigDecimal("costo")), idAlojamiento);
    }

    /** Parámetro del sistema tarifa_servicio_pct (RN-17, HU-024). */
    public Optional<BigDecimal> tarifaServicioPct() {
        return jdbc.query("SELECT valor FROM reservas.v_parametro_sistema WHERE clave = 'tarifa_servicio_pct'",
                (rs, n) -> new BigDecimal(rs.getString("valor").trim())).stream().findFirst();
    }
}
