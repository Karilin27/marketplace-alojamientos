package edu.unbosque.marketplace.logs;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.unbosque.marketplace.config.PropiedadesApp;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Envía eventos de auditoría a S6 (POST /api/internal/audit-events, cabecera X-Service-Key; HU-019).
 * Es "disparar y olvidar": si S6 no responde, la operación de negocio no falla (solo queda el log).
 */
@Component
public class PublicadorAuditoria {

    private static final Logger LOG = LoggerFactory.getLogger(PublicadorAuditoria.class);

    private final PropiedadesApp props;
    private final ObjectMapper json;
    private final HttpClient cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    public PublicadorAuditoria(PropiedadesApp props, ObjectMapper json) {
        this.props = props;
        this.json = json;
    }

    public void publicar(String usuario, String entidad, String idEntidad, String accion, String resultado) {
        String base = props.auditoriaUrl();
        if (base == null || base.isBlank()) {
            return;
        }
        try {
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("usuario", usuario);
            cuerpo.put("entidad", entidad);
            cuerpo.put("idEntidad", idEntidad);
            cuerpo.put("accion", accion);
            cuerpo.put("resultado", resultado);
            HttpRequest req = HttpRequest.newBuilder(URI.create(base.replaceAll("/+$", "") + "/api/internal/audit-events"))
                    .timeout(Duration.ofSeconds(3))
                    .header("Content-Type", "application/json")
                    .header("X-Service-Key", props.serviceKey() == null ? "" : props.serviceKey())
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo)))
                    .build();
            cliente.sendAsync(req, HttpResponse.BodyHandlers.discarding()).whenComplete((r, e) -> {
                if (e != null || r.statusCode() >= 300) {
                    LOG.warn("No se pudo publicar el evento de auditoría {} ({})", accion,
                            e != null ? e.getClass().getSimpleName() : r.statusCode());
                }
            });
        } catch (Exception e) {
            LOG.warn("No se pudo preparar el evento de auditoría {} ({})", accion, e.getClass().getSimpleName());
        }
    }
}
