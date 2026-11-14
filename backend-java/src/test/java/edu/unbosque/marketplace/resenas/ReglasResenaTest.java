package edu.unbosque.marketplace.resenas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.unbosque.marketplace.resenas.ReglasResena.Impedimento;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** HU-017 y HU-025: reglas de reseñas (casos CP-16 y CP-22 de la página 19). */
class ReglasResenaTest {

    private static final UUID HUESPED = UUID.randomUUID();
    private static final UUID RESERVA = UUID.randomUUID();

    @Test
    void cp_16_01_resenaValidaDeReservaCompletadaPropia() {
        assertTrue(ReglasResena.errores(RESERVA, 5, "Excelente ubicación").isEmpty());
        assertEquals(null, ReglasResena.impedimento(HUESPED, "COMPLETADA", false, HUESPED));
    }

    @Test
    void cp_16_03_sinReservaCompletada() {
        assertEquals(Impedimento.NO_COMPLETADA, ReglasResena.impedimento(HUESPED, "CONFIRMADA", false, HUESPED));
        assertEquals(Impedimento.NO_COMPLETADA, ReglasResena.impedimento(HUESPED, "CANCELADA", false, HUESPED));
    }

    @Test
    void cp_16_04_segundaResenaDeLaMismaReserva() {
        assertEquals(Impedimento.YA_RESENADA, ReglasResena.impedimento(HUESPED, "COMPLETADA", true, HUESPED));
    }

    @Test
    void reservaDeOtroHuesped() {
        assertEquals(Impedimento.RESERVA_AJENA,
                ReglasResena.impedimento(HUESPED, "COMPLETADA", false, UUID.randomUUID()));
    }

    @Test
    void cp_16_05_calificacionFueraDeRangoYComentarioVacio() {
        List<String> e = ReglasResena.errores(RESERVA, 6, "   ");
        assertEquals(2, e.size());
        assertTrue(e.get(0).startsWith("calificacion"));
        assertTrue(e.get(1).startsWith("comentario"));
        assertEquals(1, ReglasResena.errores(RESERVA, 0, "ok").size());
        assertEquals(1, ReglasResena.errores(null, 3, "ok").size());
    }

    @Test
    void cp_22_01_y_cp_22_03_ocultarExigeMotivo() {
        assertTrue(ReglasResena.erroresVisibilidad(false, "Lenguaje ofensivo").isEmpty());
        assertEquals(1, ReglasResena.erroresVisibilidad(false, " ").size());
        assertEquals(1, ReglasResena.erroresVisibilidad(null, "x").size());
    }

    @Test
    void cp_22_02_restablecerNoExigeMotivo() {
        assertTrue(ReglasResena.erroresVisibilidad(true, null).isEmpty());
    }
}
