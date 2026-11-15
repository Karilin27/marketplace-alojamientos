package edu.unbosque.marketplace.alojamientos;

import edu.unbosque.marketplace.alojamientos.ImagenService.ImagenSubida;
import edu.unbosque.marketplace.seguridad.FiltroAutenticacion;
import edu.unbosque.marketplace.seguridad.Sesion;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * S2 — POST /api/accommodations/images (multipart/form-data, campo "archivo"). Solo anfitrión, con token.
 * Devuelve { url, bytes, tipo }; la URL se agrega a la lista de imágenes de la publicación (D-21).
 */
@RestController
public class ImagenController {

    private final ImagenService imagenes;

    public ImagenController(ImagenService imagenes) {
        this.imagenes = imagenes;
    }

    @PostMapping(path = "/api/accommodations/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ImagenSubida subir(@RequestAttribute(FiltroAutenticacion.ATRIBUTO) Sesion sesion,
                              @RequestPart(name = "archivo", required = false) MultipartFile archivo) {
        return imagenes.subir(sesion, archivo);
    }
}
