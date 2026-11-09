package edu.unbosque.marketplace.identidad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.unbosque.marketplace.common.ReglaNegocioException;
import edu.unbosque.marketplace.common.ReglaNegocioException.Tipo;
import edu.unbosque.marketplace.identidad.LoginDtos.LoginSolicitud;
import edu.unbosque.marketplace.identidad.UsuarioRepositorio.Credenciales;
import edu.unbosque.marketplace.logs.EventLogger;
import edu.unbosque.marketplace.seguridad.JwtEmisor;
import edu.unbosque.marketplace.seguridad.JwtEmisor.TokenEmitido;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** HU-002: inicio de sesión con la base de datos simulada. */
class LoginServiceTest {

    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private UsuarioRepositorio usuarios;
    private PerfilRepositorio perfiles;
    private JwtEmisor emisor;
    private BCryptPasswordEncoder codificador;
    private LoginService servicio;

    @BeforeEach
    void preparar() {
        usuarios = mock(UsuarioRepositorio.class);
        perfiles = mock(PerfilRepositorio.class);
        emisor = mock(JwtEmisor.class);
        codificador = new BCryptPasswordEncoder(4);
        servicio = new LoginService(usuarios, perfiles, codificador, emisor, mock(EventLogger.class));

        when(usuarios.buscarPorCorreo(anyString())).thenReturn(Optional.empty());
        when(emisor.emitir(any(), anyString())).thenReturn(new TokenEmitido("token.jwt.firmado", Instant.now()));
    }

    private void existeCuenta(String estado, String hash) {
        when(usuarios.buscarPorCorreo("ana@correo.com")).thenReturn(Optional.of(
                new Credenciales(ID, "Ana", "ana@correo.com", hash, "HUESPED", estado)));
    }

    private ReglaNegocioException falla(String correo, String contrasena, Boolean reactivar) {
        return assertThrows(ReglaNegocioException.class,
                () -> servicio.iniciarSesion(new LoginSolicitud(correo, contrasena, reactivar)));
    }

    @Test
    void cuentaActivaConContrasenaCorrectaRecibeToken() {
        existeCuenta("ACTIVO", codificador.encode("Secreta123"));

        var r = servicio.iniciarSesion(new LoginSolicitud("  ANA@correo.com ", "Secreta123", null));

        assertEquals("token.jwt.firmado", r.token());
        assertEquals("Bearer", r.tipo());
        assertEquals(ID, r.usuario().id());
        assertEquals("HUESPED", r.usuario().rol());
        verify(emisor).emitir(ID, "HUESPED");
    }

    @Test
    void contrasenaIncorrectaYCorreoInexistenteDanElMismoError() {
        existeCuenta("ACTIVO", codificador.encode("Secreta123"));

        var malaClave = falla("ana@correo.com", "Incorrecta1", null);
        var noExiste = falla("nadie@correo.com", "Secreta123", null);

        assertEquals(Tipo.NO_AUTENTICADO, malaClave.tipo());
        assertEquals(malaClave.codigo(), noExiste.codigo());
        assertEquals(malaClave.getMessage(), noExiste.getMessage());
        verify(emisor, never()).emitir(any(), anyString());
    }

    @Test
    void cuentaSinHashBCryptNoPuedeEntrar() {
        existeCuenta("ACTIVO", "NO-INICIA-SESION");
        assertEquals("CREDENCIALES_INVALIDAS", falla("ana@correo.com", "NO-INICIA-SESION", null).codigo());
    }

    @Test
    void camposVaciosSonSolicitudInvalida() {
        var e = falla(" ", "", null);
        assertEquals(Tipo.SOLICITUD_INVALIDA, e.tipo());
        assertEquals(2, e.detalles().size());
        assertEquals(Tipo.SOLICITUD_INVALIDA,
                assertThrows(ReglaNegocioException.class, () -> servicio.iniciarSesion(null)).tipo());
    }

    @Test
    void cuentaRestringidaNoPuedeEntrar() {
        existeCuenta("RESTRINGIDO", codificador.encode("Secreta123"));

        var e = falla("ana@correo.com", "Secreta123", true);

        assertEquals(Tipo.PROHIBIDO, e.tipo());
        assertEquals("CUENTA_RESTRINGIDA", e.codigo());
        verify(emisor, never()).emitir(any(), anyString());
    }

    @Test
    void cuentaDesactivadaNoRecibeTokenSiNoPideReactivar() {
        existeCuenta("DESACTIVADO", codificador.encode("Secreta123"));

        var e = falla("ana@correo.com", "Secreta123", null);

        assertEquals("CUENTA_DESACTIVADA", e.codigo());
        verify(perfiles, never()).cambiarEstado(any(), anyString());
        verify(emisor, never()).emitir(any(), anyString());
    }

    @Test
    void cuentaDesactivadaSeReactivaConCredencialesValidas() {
        existeCuenta("DESACTIVADO", codificador.encode("Secreta123"));

        var r = servicio.iniciarSesion(new LoginSolicitud("ana@correo.com", "Secreta123", true));

        verify(perfiles).cambiarEstado(ID, "ACTIVO");
        assertEquals("ACTIVO", r.usuario().estado());
        assertEquals("token.jwt.firmado", r.token());
    }

    @Test
    void reactivarConContrasenaIncorrectaNoCambiaNada() {
        existeCuenta("DESACTIVADO", codificador.encode("Secreta123"));

        assertEquals("CREDENCIALES_INVALIDAS", falla("ana@correo.com", "Incorrecta1", true).codigo());
        verify(perfiles, never()).cambiarEstado(any(), anyString());
    }
}
