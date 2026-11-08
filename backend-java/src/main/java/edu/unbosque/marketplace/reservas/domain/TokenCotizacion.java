package edu.unbosque.marketplace.reservas.domain;

import edu.unbosque.marketplace.common.ReglaNegocioException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Cotización firmada y con vigencia de 30 minutos (RN-08, decisión D-14).
 * Es un token sin estado: no requiere una tabla adicional. Formato: base64url(payload).base64url(hmac).
 * El payload nunca contiene datos de pago.
 */
public final class TokenCotizacion {

    public static final Duration VIGENCIA = Duration.ofMinutes(30);
    private static final String ALGORITMO = "HmacSHA256";
    private static final String VERSION = "v1";

    public record Datos(String idAlojamiento, RangoFechas rango, int huespedes,
                        List<String> idsServicios, BigDecimal total, String idHuesped, Instant expiraEn) {
    }

    private TokenCotizacion() {
    }

    public static String emitir(Datos d, byte[] secreto) {
        List<String> ids = new ArrayList<>(d.idsServicios());
        ids.sort(String::compareTo);
        String payload = String.join("|", VERSION, d.idAlojamiento(), d.rango().llegada().toString(),
                d.rango().salida().toString(), Integer.toString(d.huespedes()), String.join(",", ids),
                d.total().toPlainString(), d.idHuesped(), Long.toString(d.expiraEn().getEpochSecond()));
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        return b64.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "."
                + b64.encodeToString(firmar(payload, secreto));
    }

    public static Datos verificar(String token, byte[] secreto, Instant ahora) {
        if (token == null || token.isBlank() || token.indexOf('.') < 1) {
            throw invalida();
        }
        String[] partes = token.split("\\.");
        if (partes.length != 2) {
            throw invalida();
        }
        String payload;
        byte[] firma;
        try {
            payload = new String(Base64.getUrlDecoder().decode(partes[0]), StandardCharsets.UTF_8);
            firma = Base64.getUrlDecoder().decode(partes[1]);
        } catch (IllegalArgumentException e) {
            throw invalida();
        }
        if (!MessageDigest.isEqual(firma, firmar(payload, secreto))) {
            throw invalida();
        }
        String[] c = payload.split("\\|", -1);
        if (c.length != 9 || !VERSION.equals(c[0])) {
            throw invalida();
        }
        Datos datos;
        try {
            List<String> ids = c[5].isEmpty() ? List.of() : List.of(c[5].split(","));
            datos = new Datos(c[1], new RangoFechas(LocalDate.parse(c[2]), LocalDate.parse(c[3])),
                    Integer.parseInt(c[4]), ids, new BigDecimal(c[6]), c[7],
                    Instant.ofEpochSecond(Long.parseLong(c[8])));
        } catch (RuntimeException e) {
            throw invalida();
        }
        if (!ahora.isBefore(datos.expiraEn())) {
            throw ReglaNegocioException.conflicto("COTIZACION_VENCIDA",
                    "La cotización venció (vigencia de 30 minutos). Vuelva a cotizar.");
        }
        return datos;
    }

    private static ReglaNegocioException invalida() {
        return ReglaNegocioException.regla("COTIZACION_INVALIDA",
                "La cotización no es válida. Vuelva a cotizar.");
    }

    private static byte[] firmar(String payload, byte[] secreto) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(secreto, ALGORITMO));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo firmar la cotización", e);
        }
    }
}
