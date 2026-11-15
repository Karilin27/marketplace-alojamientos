package edu.unbosque.marketplace.alojamientos;

import edu.unbosque.marketplace.common.ReglaNegocioException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Sube imágenes al bucket público de Supabase Storage con la API REST (POST /storage/v1/object/{bucket}/{ruta}).
 * La llave de servicio vive solo en el backend (variable SUPABASE_SERVICE_KEY en Render); el navegador nunca la ve.
 */
@Component
public class AlmacenSupabase implements AlmacenImagenes {

    private final String url;
    private final String llave;
    private final String bucket;
    private final HttpClient http;

    @Autowired
    public AlmacenSupabase(@Value("${marketplace.imagenes.supabase-url:}") String url,
                           @Value("${marketplace.imagenes.supabase-key:}") String llave,
                           @Value("${marketplace.imagenes.bucket:alojamientos}") String bucket) {
        this(url, llave, bucket, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    AlmacenSupabase(String url, String llave, String bucket, HttpClient http) {
        this.url = url == null ? "" : url.strip().replaceAll("/+$", "");
        this.llave = llave == null ? "" : llave.strip();
        this.bucket = bucket == null || bucket.isBlank() ? "alojamientos" : bucket.strip();
        this.http = http;
    }

    @Override
    public boolean configurado() {
        return !url.isEmpty() && !llave.isEmpty();
    }

    @Override
    public String guardar(String ruta, byte[] contenido, String tipoMime) {
        HttpRequest solicitud = HttpRequest.newBuilder(URI.create(url + "/storage/v1/object/" + bucket + "/" + ruta))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + llave)
                .header("apikey", llave)
                .header("Content-Type", tipoMime)
                .header("x-upsert", "false")
                .header("cache-control", "max-age=31536000")
                .POST(HttpRequest.BodyPublishers.ofByteArray(contenido))
                .build();
        int estado;
        try {
            estado = http.send(solicitud, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (IOException e) {
            throw noDisponible("sin respuesta del almacenamiento");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw noDisponible("operación interrumpida");
        }
        if (estado < 200 || estado >= 300) {
            throw noDisponible("el almacenamiento respondió HTTP " + estado);
        }
        return url + "/storage/v1/object/public/" + bucket + "/" + ruta;
    }

    private static ReglaNegocioException noDisponible(String detalle) {
        return new ReglaNegocioException(ReglaNegocioException.Tipo.SERVICIO_NO_DISPONIBLE, "ALMACEN_NO_DISPONIBLE",
                "No se pudo guardar la imagen en este momento. Intente de nuevo o pegue una URL.", List.of(detalle));
    }
}
