package edu.unbosque.marketplace.administracion;

import edu.unbosque.marketplace.administracion.AdminDtos.RecuperacionRespuesta;
import edu.unbosque.marketplace.administracion.AdminDtos.RecuperacionSolicitud;
import edu.unbosque.marketplace.administracion.AdminDtos.RestablecerSolicitud;
import edu.unbosque.marketplace.administracion.AdminRepositorio.Cuenta;
import edu.unbosque.marketplace.common.ReglaNegocioException;
import edu.unbosque.marketplace.logs.EventLogger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * HU-003 (S1) — Recuperación simulada de acceso (A-04): se genera un token de un solo uso, vigente 30 minutos,
 * que se muestra en la plataforma en lugar de enviarse por correo. Se guarda solo su hash.
 */
@Service
public class RecuperacionService {

    public static final String MENSAJE = "Si el correo está registrado, se generó un enlace de recuperación válido por 30 minutos.";

    private final AdminRepositorio repo;
    private final PasswordEncoder codificador;
    private final TransactionTemplate tx;
    private final EventLogger log;
    private final Clock reloj;
    private final SecureRandom aleatorio = new SecureRandom();

    public RecuperacionService(AdminRepositorio repo, PasswordEncoder codificador, TransactionTemplate tx,
                               EventLogger log, Clock reloj) {
        this.repo = repo;
        this.codificador = codificador;
        this.tx = tx;
        this.log = log;
        this.reloj = reloj;
    }

    // CA-01, CA-02
    public RecuperacionRespuesta solicitar(RecuperacionSolicitud s) {
        String correo = s == null ? null : ReglasAdministracion.texto(s.correo());
        if (correo == null) {
            throw ReglaNegocioException.solicitudInvalida("CORREO_REQUERIDO", "Ingrese su correo.", "correo");
        }
        Optional<Cuenta> cuenta = repo.cuentaPorCorreo(correo);
        if (cuenta.isEmpty() || "RESTRINGIDO".equals(cuenta.get().estado())) {
            log.info("RECUPERACION_SOLICITADA", null, "usuario", null, "SIN_CUENTA", Map.of());
            return new RecuperacionRespuesta(MENSAJE, null, null);                  // mismo mensaje, sin token
        }
        byte[] bytes = new byte[32];
        aleatorio.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expira = reloj.instant().plus(Duration.ofMinutes(ReglasAdministracion.MINUTOS_TOKEN));
        UUID id = cuenta.get().id();
        tx.executeWithoutResult(t -> repo.guardarToken(hash(token), id, expira));
        log.auditado("RECUPERACION_SOLICITADA", id.toString(), "usuario", id.toString(), "EXITO", Map.of());  // CA-05
        return new RecuperacionRespuesta(MENSAJE, token, expira);
    }

    // CA-03, CA-04, CA-05
    public Map<String, String> restablecer(RestablecerSolicitud s) {
        String token = s == null ? null : ReglasAdministracion.texto(s.token());
        String nueva = s == null ? null : s.nuevaContrasena();
        List<String> errores = ReglasAdministracion.erroresNuevaContrasena(token, nueva);
        if (!errores.isEmpty()) {
            throw ReglaNegocioException.solicitudInvalida("RESTABLECER_INVALIDO", "Revise los datos.",
                    errores.toArray(String[]::new));
        }
        String h = hash(token);
        UUID id = tx.execute(t -> {
            UUID usuario = repo.tokenValido(h, reloj.instant()).orElseThrow(() -> ReglaNegocioException.regla(
                    "TOKEN_INVALIDO", "El enlace no es válido, ya se usó o venció. Solicite uno nuevo."));
            repo.cambiarContrasena(usuario, codificador.encode(nueva));
            repo.usarToken(h);                                                       // un solo uso
            return usuario;
        });
        log.auditado("CONTRASENA_RESTABLECIDA", id.toString(), "usuario", id.toString(), "EXITO", Map.of());
        return Map.of("mensaje", "Su contraseña fue actualizada. Ya puede iniciar sesión.");
    }

    static String hash(String token) {
        String valor = token == null ? "" : token;                                  // nunca coincide con un token real
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
