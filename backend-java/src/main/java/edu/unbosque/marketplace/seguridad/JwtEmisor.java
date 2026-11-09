package edu.unbosque.marketplace.seguridad;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.unbosque.marketplace.config.PropiedadesApp;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Emite el JWT HS256 de S1 (HU-002) con el formato que valida {@link JwtVerificador}:
 * cabecera {"alg":"HS256","typ":"JWT"} y claims "sub" (UUID del usuario), "rol", "iat" y "exp".
 */
@Component
public class JwtEmisor {

    static final int SECRETO_MIN = 32;

    private static final Logger LOG = LoggerFactory.getLogger(JwtEmisor.class);

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    private final byte[] secreto;
    private final ObjectMapper json;
    private final Clock reloj;
    private final Duration vigencia;

    public JwtEmisor(PropiedadesApp props, ObjectMapper json, Clock reloj,
                     @Value("${marketplace.jwt-horas:8}") long horas) {
        this.secreto = props.jwtSecret() == null ? new byte[0] : props.jwtSecret().getBytes(StandardCharsets.UTF_8);
        this.json = json;
        this.reloj = reloj;
        this.vigencia = Duration.ofHours(horas);
        if (secreto.length < SECRETO_MIN) {
            LOG.warn("JWT_SECRET no está definido o tiene menos de {} caracteres: el inicio de sesión no podrá "
                    + "emitir tokens hasta que se configure.", SECRETO_MIN);
        }
    }

    public record TokenEmitido(String valor, Instant expiraEn) {
    }

    public TokenEmitido emitir(UUID idUsuario, String rol) {
        if (secreto.length < SECRETO_MIN) {
            throw new IllegalStateException("marketplace.jwt-secret debe tener al menos 32 caracteres.");
        }
        Instant ahora = reloj.instant();
        Instant expira = ahora.plus(vigencia);

        Map<String, Object> cabecera = new LinkedHashMap<>();
        cabecera.put("alg", "HS256");
        cabecera.put("typ", "JWT");
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("sub", idUsuario.toString());
        datos.put("rol", rol);
        datos.put("iat", ahora.getEpochSecond());
        datos.put("exp", expira.getEpochSecond());

        try {
            String contenido = B64.encodeToString(json.writeValueAsBytes(cabecera)) + "."
                    + B64.encodeToString(json.writeValueAsBytes(datos));
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto, "HmacSHA256"));
            String firma = B64.encodeToString(mac.doFinal(contenido.getBytes(StandardCharsets.UTF_8)));
            return new TokenEmitido(contenido + "." + firma, expira);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo firmar el token de sesión.", e);
        }
    }
}
