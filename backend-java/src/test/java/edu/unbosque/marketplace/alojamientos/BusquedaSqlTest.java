package edu.unbosque.marketplace.alojamientos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.unbosque.marketplace.alojamientos.AlojamientoRepositorio.Filtros;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * HU-009: la consulta de búsqueda se arma uniendo fragmentos. Si un fragmento con text block queda pegado al
 * parámetro anterior ("?AND"), PostgreSQL responde "trailing junk after parameter" y la búsqueda devuelve 500.
 */
class BusquedaSqlTest {

    @Test
    void combinarFiltrosConFechasYServiciosNoPegaElParametroAlAnd() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        AlojamientoRepositorio repo = new AlojamientoRepositorio(jdbc);
        Filtros f = new Filtros("Medellín", LocalDate.of(2027, 1, 10), LocalDate.of(2027, 1, 13), 2, "APARTAMENTO",
                BigDecimal.ONE, BigDecimal.TEN, List.of("WiFi", "Cocina"));

        long[] total = new long[1];
        repo.buscar(f, 0, 12, total);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(sql.capture(), eq(Long.class), any(Object[].class));
        String consulta = sql.getValue();
        assertFalse(consulta.matches("(?s).*\\?AND.*"), consulta);
        assertTrue(consulta.matches("(?s).*<= \\?\\s+AND EXISTS.*"), consulta);
        assertTrue(consulta.matches("(?s).*\\)\\s+AND NOT EXISTS.*"), consulta);
        assertEquals(0L, total[0]);
    }

    @Test
    void tipoConFechasSinServicios() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(3L);
        Filtros f = new Filtros(null, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 4), null, "CASA",
                null, null, List.of());

        long[] total = new long[1];
        new AlojamientoRepositorio(jdbc).buscar(f, 0, 12, total);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(sql.capture(), eq(Long.class), any(Object[].class));
        assertTrue(sql.getValue().matches("(?s).*a\\.tipo = \\?\\s+AND NOT EXISTS.*"), sql.getValue());
        assertEquals(3L, total[0]);
    }
}
