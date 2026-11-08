package edu.unbosque.marketplace.seguridad;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.unbosque.marketplace.common.ReglaNegocioException;
import edu.unbosque.marketplace.config.PropiedadesApp;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Verifica el JWT HS256 que emite S1 (HU-002). Supuestos a confirmar con quien tiene S1:
 * algoritmo HS256 con el mismo secreto, claim "sub" = UUID del usuario, claim "rol".
 */
@Component
public class JwtVerificador {

    private final byte[] secreto;
    private final ObjectMapper json;

    public JwtVerificador(PropiedadesApp props, ObjectMapper json) {
        this.secreto = props.jwtSecret() == null ? new byte[0] : props.jwtSecret().getBytes(StandardCharsets.UTF_8);
        this.json = json;
    }

    public Sesion verificar(String token, Instant ahora) {
        try {
            String[] p = token.split("\\.");
            if (p.length != 3 || secreto.length == 0) {
                throw invalido();
            }
            JsonNode cabecera = json.readTree(Base64.getUrlDecoder().decode(p[0]));
            if (!"HS256".equals(cabecera.path("alg").asText())) {
                throw invalido();
            }
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto, "HmacSHA256"));
            byte[] esperada = mac.doFinal((p[0] + "." + p[1]).getBytes(StandardCharsets.UTF_8));
            if (!MessageDigest.isEqual(esperada, Base64.getUrlDecoder().decode(p[2]))) {
                throw invalido();
            }
            JsonNode datos = json.readTree(Base64.getUrlDecoder().decode(p[1]));
            if (datos.hasNonNull("exp") && datos.get("exp").asLong() <= ahora.getEpochSecond()) {
                throw ReglaNegocioException.noAutenticado("La sesión expiró; inicie sesión de nuevo.");
            }
            return new Sesion(UUID.fromString(datos.path("sub").asText()), rol(datos.path("rol").asText()));
        } catch (ReglaNegocioException e) {
            throw e;
        } catch (Exception e) {
            throw invalido();
        }
    }

    private static Sesion.Rol rol(String texto) {
        String limpio = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toUpperCase().replace("ROLE_", "");
        return switch (limpio) {
            case "HUESPED" -> Sesion.Rol.HUESPED;
            case "ANFITRION" -> Sesion.Rol.ANFITRION;
            case "ADMINISTRADOR", "ADMIN" -> Sesion.Rol.ADMINISTRADOR;
            default -> throw invalido();
        };
    }

    private static ReglaNegocioException invalido() {
        return ReglaNegocioException.noAutenticado("Token ausente, inválido o expirado.");
    }
}
