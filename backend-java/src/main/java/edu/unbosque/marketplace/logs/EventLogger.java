package edu.unbosque.marketplace.logs;

import java.time.Clock;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Registro de eventos de negocio y errores en JSON estructurado (HU-026, RNF de trazabilidad).
 * Escribe en el logger "eventos"; logback-spring.xml lo imprime tal cual (una línea JSON por evento).
 */
@Component
public class EventLogger {

    private static final Logger LOG = LoggerFactory.getLogger("eventos");

    private final Clock reloj;
    private final PublicadorAuditoria auditoria;

    public EventLogger(Clock reloj, PublicadorAuditoria auditoria) {
        this.reloj = reloj;
        this.auditoria = auditoria;
    }

    public void info(String evento, String usuario, String entidad, String idEntidad, String resultado,
                     Map<String, Object> detalle) {
        escribir(NivelLog.INFO, evento, usuario, entidad, idEntidad, resultado, detalle);
    }

    public void warn(String evento, String usuario, String entidad, String idEntidad, String resultado,
                     Map<String, Object> detalle) {
        escribir(NivelLog.WARN, evento, usuario, entidad, idEntidad, resultado, detalle);
    }

    public void error(String evento, String usuario, String entidad, String idEntidad, String resultado,
                      Map<String, Object> detalle) {
        escribir(NivelLog.ERROR, evento, usuario, entidad, idEntidad, resultado, detalle);
    }

    /** Evento relevante para auditoría: se registra en el log y además se publica a S6. */
    public void auditado(String evento, String usuario, String entidad, String idEntidad, String resultado,
                         Map<String, Object> detalle) {
        escribir("EXITO".equals(resultado) ? NivelLog.INFO : NivelLog.WARN, evento, usuario, entidad, idEntidad,
                resultado, detalle);
        auditoria.publicar(usuario, entidad, idEntidad, evento, resultado);
    }

    private void escribir(NivelLog nivel, String evento, String usuario, String entidad, String idEntidad,
                          String resultado, Map<String, Object> detalle) {
        String linea = StructuredLogFormatter.formatear(reloj.instant(), nivel, evento, usuario, entidad,
                idEntidad, resultado, detalle);
        switch (nivel) {
            case ERROR -> LOG.error(linea);
            case WARN -> LOG.warn(linea);
            case DEBUG -> LOG.debug(linea);
            default -> LOG.info(linea);
        }
    }
}
