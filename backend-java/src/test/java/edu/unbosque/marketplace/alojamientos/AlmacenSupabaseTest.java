package edu.unbosque.marketplace.alojamientos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import edu.unbosque.marketplace.common.ReglaNegocioException;
import edu.unbosque.marketplace.common.ReglaNegocioException.Tipo;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** D-21: cliente de Supabase Storage contra un servidor HTTP local que simula la API. */
class AlmacenSupabaseTest {

    private HttpServer servidor;
    private String base;
    private final AtomicReference<String> ruta = new AtomicReference<>();
    private final AtomicReference<String> autorizacion = new AtomicReference<>();
    private final AtomicReference<String> apikey = new AtomicReference<>();
    private final AtomicReference<String> tipo = new AtomicReference<>();
    private final AtomicReference<Integer> bytes = new AtomicReference<>();
    private volatile int estado = 200;

    @BeforeEach
    void iniciar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", ex -> {
            ruta.set(ex.getRequestMethod() + " " + ex.getRequestURI().getPath());
            autorizacion.set(ex.getRequestHeaders().getFirst("Authorization"));
            apikey.set(ex.getRequestHeaders().getFirst("apikey"));
            tipo.set(ex.getRequestHeaders().getFirst("Content-Type"));
            bytes.set(ex.getRequestBody().readAllBytes().length);
            ex.sendResponseHeaders(estado, -1);
            ex.close();
        });
        servidor.start();
        base = "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    @AfterEach
    void detener() {
        servidor.stop(0);
    }

    private AlmacenSupabase almacen(String url, String llave) {
        return new AlmacenSupabase(url, llave, "alojamientos", HttpClient.newHttpClient());
    }

    @Test
    void subeAlBucketYDevuelveLaUrlPublica() {
        String url = almacen(base + "/", "llave-de-prueba").guardar("anf/foto.jpg", new byte[10], "image/jpeg");

        assertEquals(base + "/storage/v1/object/public/alojamientos/anf/foto.jpg", url);
        assertEquals("POST /storage/v1/object/alojamientos/anf/foto.jpg", ruta.get());
        assertEquals("Bearer llave-de-prueba", autorizacion.get());
        assertEquals("llave-de-prueba", apikey.get());
        assertEquals("image/jpeg", tipo.get());
        assertEquals(10, bytes.get());
    }

    @Test
    void respuestaDeErrorDelAlmacenamiento() {
        estado = 404;
        var e = assertThrows(ReglaNegocioException.class,
                () -> almacen(base, "llave").guardar("anf/foto.png", new byte[3], "image/png"));
        assertEquals(Tipo.SERVICIO_NO_DISPONIBLE, e.tipo());
        assertTrue(e.detalles().get(0).contains("404"));
    }

    @Test
    void almacenamientoSinRespuesta() {
        servidor.stop(0);
        var e = assertThrows(ReglaNegocioException.class,
                () -> almacen(base, "llave").guardar("anf/foto.png", new byte[3], "image/png"));
        assertEquals("ALMACEN_NO_DISPONIBLE", e.codigo());
    }

    @Test
    void configuradoSoloConUrlYLlave() {
        assertTrue(almacen(base, "llave").configurado());
        assertFalse(almacen("", "llave").configurado());
        assertFalse(almacen(base, "  ").configurado());
        assertFalse(new AlmacenSupabase(null, null, null).configurado());
    }
}
