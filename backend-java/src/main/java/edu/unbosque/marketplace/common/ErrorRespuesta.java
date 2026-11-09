package edu.unbosque.marketplace.common;

import java.util.List;

/** Formato de error común a todos los servicios (página 13): {"codigo","mensaje","detalles"}. */
public record ErrorRespuesta(String codigo, String mensaje, List<String> detalles) {
}
