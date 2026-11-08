package edu.unbosque.marketplace.common;

import edu.unbosque.marketplace.logs.EventLogger;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final EventLogger log;

    public GlobalExceptionHandler(EventLogger log) {
        this.log = log;
    }

    @ExceptionHandler(ReglaNegocioException.class)
    public ResponseEntity<ErrorRespuesta> negocio(ReglaNegocioException e) {
        return ResponseEntity.status(e.tipo().http())
                .body(new ErrorRespuesta(e.codigo(), e.getMessage(), e.detalles()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorRespuesta> validacion(MethodArgumentNotValidException e) {
        List<String> detalles = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage()).toList();
        return ResponseEntity.badRequest()
                .body(new ErrorRespuesta("VALIDACION", "La solicitud tiene campos inválidos.", detalles));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorRespuesta> cuerpo(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(new ErrorRespuesta("CUERPO_INVALIDO",
                "El cuerpo de la solicitud no es un JSON válido o tiene valores con formato incorrecto.", List.of()));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorRespuesta> cabecera(MissingRequestHeaderException e) {
        return ResponseEntity.badRequest().body(new ErrorRespuesta("CABECERA_REQUERIDA",
                "Falta la cabecera obligatoria " + e.getHeaderName() + ".", List.of(e.getHeaderName())));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorRespuesta> parametro(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(new ErrorRespuesta("PARAMETRO_INVALIDO",
                "El parámetro '" + e.getName() + "' tiene un formato inválido.", List.of(e.getName())));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorRespuesta> integridad(DataIntegrityViolationException e) {
        log.error("ERROR_INTEGRIDAD", null, null, null, "FALLO", Map.of("causa", e.getClass().getSimpleName()));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorRespuesta("CONFLICTO_DE_DATOS",
                "La operación entra en conflicto con el estado actual de los datos.", List.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorRespuesta> inesperado(Exception e) {
        if (e instanceof org.springframework.web.ErrorResponse er) {
            // 404 de ruta, 405, 415, etc.: respetar el estado que decidió Spring.
            return ResponseEntity.status(er.getStatusCode()).body(new ErrorRespuesta("SOLICITUD_NO_VALIDA",
                    "La solicitud no puede procesarse: " + er.getStatusCode(), List.of()));
        }
        log.error("ERROR_INTERNO", null, null, null, "FALLO",
                Map.of("tipo", e.getClass().getSimpleName(), "mensaje", String.valueOf(e.getMessage())));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorRespuesta("ERROR_INTERNO",
                "Ocurrió un error inesperado. Intente de nuevo más tarde.", List.of()));
    }
}
