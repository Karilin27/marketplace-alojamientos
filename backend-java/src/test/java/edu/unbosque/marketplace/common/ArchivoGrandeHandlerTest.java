package edu.unbosque.marketplace.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import edu.unbosque.marketplace.logs.EventLogger;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** D-21: un archivo que supera el límite técnico de multipart responde 413 con el formato de error del contrato. */
class ArchivoGrandeHandlerTest {

    @Test
    void respondeCuatroTrece() {
        var r = new GlobalExceptionHandler(mock(EventLogger.class))
                .archivoGrande(new MaxUploadSizeExceededException(6L * 1024 * 1024));
        assertEquals(413, r.getStatusCode().value());
        assertEquals("IMAGEN_MUY_GRANDE", r.getBody().codigo());
    }

    @Test
    void nuevosTiposDeError() {
        assertEquals(413, ReglaNegocioException.demasiadoGrande("X", "m").tipo().http());
        assertEquals(503, ReglaNegocioException.noDisponible("X", "m").tipo().http());
    }
}
