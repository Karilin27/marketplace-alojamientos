package edu.unbosque.marketplace.alojamientos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.unbosque.marketplace.alojamientos.ImagenService.ImagenSubida;
import edu.unbosque.marketplace.common.ReglaNegocioException;
import edu.unbosque.marketplace.common.ReglaNegocioException.Tipo;
import edu.unbosque.marketplace.logs.EventLogger;
import edu.unbosque.marketplace.seguridad.Sesion;
import edu.unbosque.marketplace.seguridad.Sesion.Rol;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

/** D-21: subida de imágenes del anfitrión con el almacenamiento simulado. */
class ImagenServiceTest {

    private static final UUID ANFITRION = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final String URL = "https://x.supabase.co/storage/v1/object/public/alojamientos/";

    private AlmacenImagenes almacen;
    private ImagenService servicio;
    private ImagenController controlador;
    private final Sesion anfitrion = new Sesion(ANFITRION, Rol.ANFITRION);

    @BeforeEach
    void preparar() {
        almacen = mock(AlmacenImagenes.class);
        when(almacen.configurado()).thenReturn(true);
        when(almacen.guardar(anyString(), any(), anyString())).thenAnswer(i -> URL + i.getArgument(0));
        servicio = new ImagenService(almacen, mock(EventLogger.class));
        controlador = new ImagenController(servicio);
    }

    private static MockMultipartFile archivo(byte[] contenido) {
        return new MockMultipartFile("archivo", "foto.jpg", "image/jpeg", contenido);
    }

    private ReglaNegocioException falla(Sesion sesion, MultipartFile a) {
        return assertThrows(ReglaNegocioException.class, () -> servicio.subir(sesion, a));
    }

    @Test
    void subeUnaFotoJpgYDevuelveLaUrlPublica() {
        ImagenSubida r = controlador.subir(anfitrion, archivo(ReglasImagenTest.jpg()));

        ArgumentCaptor<String> ruta = ArgumentCaptor.forClass(String.class);
        verify(almacen).guardar(ruta.capture(), any(), eq("image/jpeg"));
        assertTrue(ruta.getValue().startsWith(ANFITRION + "/"));
        assertTrue(ruta.getValue().endsWith(".jpg"));
        assertEquals(URL + ruta.getValue(), r.url());
        assertEquals(32, r.bytes());
        assertEquals("image/jpeg", r.tipo());
    }

    @Test
    void elFormatoSeDecidePorElContenidoNoPorElNombre() {
        var pngConNombreJpg = new MockMultipartFile("archivo", "foto.jpg", "image/jpeg", ReglasImagenTest.png());
        assertEquals("image/png", servicio.subir(anfitrion, pngConNombreJpg).tipo());
    }

    @Test
    void soloElAnfitrionPuedeSubir() {
        for (Rol rol : new Rol[] {Rol.HUESPED, Rol.ADMINISTRADOR}) {
            assertEquals(Tipo.PROHIBIDO, falla(new Sesion(UUID.randomUUID(), rol), archivo(ReglasImagenTest.jpg())).tipo());
        }
        verify(almacen, never()).guardar(anyString(), any(), anyString());
    }

    @Test
    void sinConfiguracionResponde503() {
        when(almacen.configurado()).thenReturn(false);
        var e = falla(anfitrion, archivo(ReglasImagenTest.jpg()));
        assertEquals(Tipo.SERVICIO_NO_DISPONIBLE, e.tipo());
        assertEquals("SUBIDA_NO_CONFIGURADA", e.codigo());
    }

    @Test
    void archivoVacioONulo() {
        assertEquals("IMAGEN_REQUERIDA", falla(anfitrion, null).codigo());
        assertEquals("IMAGEN_REQUERIDA", falla(anfitrion, archivo(new byte[0])).codigo());
    }

    @Test
    void archivoDeMasDeCincoMegas() {
        var e = falla(anfitrion, archivo(new byte[(int) ReglasImagen.MAX_BYTES + 1]));
        assertEquals(Tipo.CARGA_DEMASIADO_GRANDE, e.tipo());
        assertEquals("IMAGEN_MUY_GRANDE", e.codigo());
    }

    @Test
    void formatoNoPermitido() {
        var e = falla(anfitrion, archivo("esto no es una imagen, es texto plano".getBytes()));
        assertEquals(Tipo.SOLICITUD_INVALIDA, e.tipo());
        assertEquals("FORMATO_NO_PERMITIDO", e.codigo());
    }

    @Test
    void archivoIlegible() throws IOException {
        MultipartFile roto = mock(MultipartFile.class);
        when(roto.isEmpty()).thenReturn(false);
        when(roto.getSize()).thenReturn(100L);
        when(roto.getBytes()).thenThrow(new IOException("disco"));
        assertEquals("IMAGEN_ILEGIBLE", falla(anfitrion, roto).codigo());
    }

    @Test
    void errorDelAlmacenamientoSePropaga() {
        when(almacen.guardar(anyString(), any(), anyString())).thenThrow(
                ReglaNegocioException.noDisponible("ALMACEN_NO_DISPONIBLE", "caído"));
        assertEquals("ALMACEN_NO_DISPONIBLE", falla(anfitrion, archivo(ReglasImagenTest.jpg())).codigo());
    }
}
