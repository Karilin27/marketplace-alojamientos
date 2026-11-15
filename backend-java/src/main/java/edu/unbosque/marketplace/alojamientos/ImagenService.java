package edu.unbosque.marketplace.alojamientos;

import edu.unbosque.marketplace.alojamientos.ReglasImagen.Formato;
import edu.unbosque.marketplace.common.ReglaNegocioException;
import edu.unbosque.marketplace.logs.EventLogger;
import edu.unbosque.marketplace.seguridad.Sesion;
import edu.unbosque.marketplace.seguridad.Sesion.Rol;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * HU-005 CA-03 y HU-006 (cambio D-21): el anfitrión sube una foto desde su computador y recibe la URL pública,
 * que luego se guarda en la publicación igual que una URL pegada a mano.
 */
@Service
public class ImagenService {

    /** Respuesta de la subida: URL pública, tamaño y tipo MIME detectado. */
    public record ImagenSubida(String url, long bytes, String tipo) {
    }

    private final AlmacenImagenes almacen;
    private final EventLogger log;

    public ImagenService(AlmacenImagenes almacen, EventLogger log) {
        this.almacen = almacen;
        this.log = log;
    }

    public ImagenSubida subir(Sesion sesion, MultipartFile archivo) {
        sesion.exigirRol(Rol.ANFITRION);                                              // HU-005 CA-05
        if (!almacen.configurado()) {
            throw ReglaNegocioException.noDisponible("SUBIDA_NO_CONFIGURADA",
                    "La subida de fotos no está habilitada en este servidor. Pegue la URL de la imagen.");
        }
        if (archivo == null || archivo.isEmpty()) {
            throw ReglaNegocioException.solicitudInvalida("IMAGEN_REQUERIDA", "Seleccione una imagen.", "archivo");
        }
        if (ReglasImagen.excedeTamano(archivo.getSize())) {
            throw ReglaNegocioException.demasiadoGrande("IMAGEN_MUY_GRANDE",
                    "La imagen supera el máximo de 5 MB.", "archivo: máximo 5 MB");
        }
        byte[] contenido;
        try {
            contenido = archivo.getBytes();
        } catch (IOException e) {
            throw ReglaNegocioException.solicitudInvalida("IMAGEN_ILEGIBLE", "No se pudo leer la imagen.", "archivo");
        }
        Formato formato = ReglasImagen.detectar(contenido);
        if (formato == null) {
            throw ReglaNegocioException.solicitudInvalida("FORMATO_NO_PERMITIDO",
                    "Solo se aceptan imágenes JPG, PNG o WEBP.", "archivo: formato no permitido");
        }
        String ruta = ReglasImagen.ruta(sesion.id().toString(), UUID.randomUUID().toString(), formato);
        String url = almacen.guardar(ruta, contenido, formato.mime());
        log.info("IMAGEN_SUBIDA", sesion.id().toString(), "imagen", ruta, "EXITO",
                Map.of("bytes", contenido.length, "tipo", formato.mime()));
        return new ImagenSubida(url, contenido.length, formato.mime());
    }
}
