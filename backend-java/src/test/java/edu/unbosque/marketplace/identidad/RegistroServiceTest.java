package edu.unbosque.marketplace.identidad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import edu.unbosque.marketplace.common.ReglaNegocioException;
import edu.unbosque.marketplace.identidad.RegistroDtos.RegistroRespuesta;
import edu.unbosque.marketplace.identidad.RegistroDtos.RegistroSolicitud;
import edu.unbosque.marketplace.logs.EventLogger;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** HU-001: flujo del registro con la base de datos simulada. */
class RegistroServiceTest {

    private UsuarioRepositorio repo;
    private PlatformTransactionManager tm;
    private BCryptPasswordEncoder codificador;
    private RegistroService servicio;

    @BeforeEach
    void preparar() {
        repo = mock(UsuarioRepositorio.class);
        tm = mock(PlatformTransactionManager.class);
        codificador = new BCryptPasswordEncoder(4);   // costo bajo solo para que la prueba sea rápida
        servicio = new RegistroService(repo, codificador, new TransactionTemplate(tm), mock(EventLogger.class));

        when(repo.insertar(anyString(), anyString(), anyString(), anyString())).thenAnswer(inv -> new RegistroRespuesta(
                UUID.randomUUID(), inv.getArgument(0), inv.getArgument(1), inv.getArgument(3), "ACTIVO",
                OffsetDateTime.now()));
    }

    @Test
    void registraConContrasenaCifradaConBCrypt() {
        RegistroRespuesta r = servicio.registrar(new RegistroSolicitud(" Ana ", "Ana@Correo.com", "Secreta123", "anfitrion"));

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(repo).insertar(eq("Ana"), eq("ana@correo.com"), hash.capture(), eq("ANFITRION"));
        assertNotEquals("Secreta123", hash.getValue());
        assertTrue(hash.getValue().startsWith("$2"));
        assertTrue(codificador.matches("Secreta123", hash.getValue()));
        assertEquals("ANFITRION", r.rol());
        verify(tm).commit(any());
    }

    @Test
    void datosInvalidosNoTocanLaBaseDeDatos() {
        var e = assertThrows(ReglaNegocioException.class,
                () -> servicio.registrar(new RegistroSolicitud("Ana", "ana@correo", "123", "ADMINISTRADOR")));

        assertEquals(ReglaNegocioException.Tipo.SOLICITUD_INVALIDA, e.tipo());
        assertEquals("REGISTRO_INVALIDO", e.codigo());
        assertEquals(3, e.detalles().size());
        verifyNoInteractions(repo, tm);
    }

    @Test
    void cuerpoVacioSeRechaza() {
        var e = assertThrows(ReglaNegocioException.class, () -> servicio.registrar(null));
        assertEquals(ReglaNegocioException.Tipo.SOLICITUD_INVALIDA, e.tipo());
    }

    @Test
    void correoYaRegistradoDevuelveConflictoSinInsertar() {
        when(repo.existeCorreo("ana@correo.com")).thenReturn(true);

        var e = assertThrows(ReglaNegocioException.class,
                () -> servicio.registrar(new RegistroSolicitud("Ana", "ANA@correo.com", "Secreta123", "HUESPED")));

        assertEquals(ReglaNegocioException.Tipo.CONFLICTO, e.tipo());
        assertEquals("CORREO_YA_REGISTRADO", e.codigo());
        verify(repo, never()).insertar(anyString(), anyString(), anyString(), anyString());
        verify(tm).rollback(any());
    }

    @Test
    void registrosSimultaneosConElMismoCorreoTambienDanConflicto() {
        when(repo.insertar(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new DuplicateKeyException("usuario_correo_uk"));

        var e = assertThrows(ReglaNegocioException.class,
                () -> servicio.registrar(new RegistroSolicitud("Ana", "ana@correo.com", "Secreta123", "HUESPED")));

        assertEquals("CORREO_YA_REGISTRADO", e.codigo());
    }
}
