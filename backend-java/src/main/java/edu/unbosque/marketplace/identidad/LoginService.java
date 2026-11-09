package edu.unbosque.marketplace.identidad;

import edu.unbosque.marketplace.common.ReglaNegocioException;
import edu.unbosque.marketplace.common.ReglaNegocioException.Tipo;
import edu.unbosque.marketplace.identidad.LoginDtos.LoginRespuesta;
import edu.unbosque.marketplace.identidad.LoginDtos.LoginSolicitud;
import edu.unbosque.marketplace.identidad.LoginDtos.UsuarioSesion;
import edu.unbosque.marketplace.identidad.UsuarioRepositorio.Credenciales;
import edu.unbosque.marketplace.logs.EventLogger;
import edu.unbosque.marketplace.seguridad.JwtEmisor;
import edu.unbosque.marketplace.seguridad.JwtEmisor.TokenEmitido;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * HU-002: inicio de sesión con correo y contraseña. Emite el JWT que exigen los demás módulos.
 * <ul>
 *   <li>ACTIVO: entra.</li>
 *   <li>DESACTIVADO: no recibe token; puede reactivar la cuenta con sus credenciales (HU-004 CA-07).</li>
 *   <li>RESTRINGIDO: no puede entrar (RN-24).</li>
 * </ul>
 */
@Service
public class LoginService {

    private final UsuarioRepositorio usuarios;
    private final PerfilRepositorio perfiles;
    private final PasswordEncoder codificador;
    private final JwtEmisor emisor;
    private final EventLogger log;

    /**
     * Hash señuelo: si el correo no existe se compara igual contra este hash, para que la respuesta
     * tarde lo mismo y no se pueda saber qué correos están registrados midiendo el tiempo.
     */
    private final String hashSenuelo;

    public LoginService(UsuarioRepositorio usuarios, PerfilRepositorio perfiles, PasswordEncoder codificador,
                        JwtEmisor emisor, EventLogger log) {
        this.usuarios = usuarios;
        this.perfiles = perfiles;
        this.codificador = codificador;
        this.emisor = emisor;
        this.log = log;
        this.hashSenuelo = codificador.encode("senuelo-para-tiempo-constante");
    }

    public LoginRespuesta iniciarSesion(LoginSolicitud solicitud) {
        String correo = solicitud == null || solicitud.correo() == null
                ? "" : solicitud.correo().strip().toLowerCase(Locale.ROOT);
        String contrasena = solicitud == null || solicitud.contrasena() == null ? "" : solicitud.contrasena();
        List<String> faltan = new ArrayList<>();
        if (correo.isEmpty()) {
            faltan.add("correo: es obligatorio.");
        }
        if (contrasena.isEmpty()) {
            faltan.add("contrasena: es obligatoria.");
        }
        if (!faltan.isEmpty()) {
            throw ReglaNegocioException.solicitudInvalida("LOGIN_INVALIDO",
                    "Debe ingresar su correo y su contraseña.", faltan.toArray(String[]::new));
        }

        Optional<Credenciales> encontrada = usuarios.buscarPorCorreo(correo);
        if (encontrada.isEmpty()) {
            codificador.matches(contrasena, hashSenuelo);
            log.warn("INICIO_SESION_FALLIDO", null, "usuario", null, "RECHAZADO", Map.of("motivo", "CREDENCIALES"));
            throw credencialesInvalidas();
        }
        Credenciales c = encontrada.get();
        if (!contrasenaCorrecta(contrasena, c.contrasenaHash())) {
            log.warn("INICIO_SESION_FALLIDO", c.id().toString(), "usuario", c.id().toString(), "RECHAZADO",
                    Map.of("motivo", "CREDENCIALES"));
            throw credencialesInvalidas();
        }

        // A partir de aquí la persona demostró conocer la contraseña: se le puede explicar el estado de su cuenta.
        String estado = c.estado();
        if (PerfilService.RESTRINGIDO.equals(estado)) {
            log.auditado("INICIO_SESION_RECHAZADO", c.id().toString(), "usuario", c.id().toString(), "RECHAZADO",
                    Map.of("estado", estado));
            throw new ReglaNegocioException(Tipo.PROHIBIDO, "CUENTA_RESTRINGIDA",
                    "Su cuenta fue restringida por un administrador. Comuníquese con soporte.", List.of());
        }
        if (PerfilService.DESACTIVADO.equals(estado)) {
            if (!Boolean.TRUE.equals(solicitud.reactivar())) {
                throw new ReglaNegocioException(Tipo.PROHIBIDO, "CUENTA_DESACTIVADA",
                        "Su cuenta está desactivada. Puede reactivarla para volver a ingresar.", List.of());
            }
            perfiles.cambiarEstado(c.id(), PerfilService.ACTIVO);                   // HU-004 CA-07
            estado = PerfilService.ACTIVO;
            log.auditado("CUENTA_REACTIVADA", c.id().toString(), "usuario", c.id().toString(), "EXITO",
                    Map.of("origen", "inicio_sesion"));
        }

        TokenEmitido token = emisor.emitir(c.id(), c.rol());
        log.auditado("INICIO_SESION", c.id().toString(), "usuario", c.id().toString(), "EXITO", Map.of());
        return new LoginRespuesta(token.valor(), "Bearer", token.expiraEn(),
                new UsuarioSesion(c.id(), c.nombre(), c.correo(), c.rol(), estado));
    }

    /** Usuarios sin un hash BCrypt válido (p. ej. datos de prueba antiguos) no pueden iniciar sesión. */
    private boolean contrasenaCorrecta(String contrasena, String hash) {
        if (hash == null || !hash.startsWith("$2")) {
            codificador.matches(contrasena, hashSenuelo);
            return false;
        }
        return codificador.matches(contrasena, hash);
    }

    private static ReglaNegocioException credencialesInvalidas() {
        // Mismo mensaje si el correo no existe o si la contraseña es incorrecta.
        return new ReglaNegocioException(Tipo.NO_AUTENTICADO, "CREDENCIALES_INVALIDAS",
                "Correo electrónico o contraseña incorrectos.", List.of());
    }
}
