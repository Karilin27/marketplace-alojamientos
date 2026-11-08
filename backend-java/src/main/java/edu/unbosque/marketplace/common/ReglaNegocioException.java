package edu.unbosque.marketplace.common;

import java.util.List;

/**
 * Error de negocio o de validación con código estable y tipo HTTP asociado.
 * Formato de respuesta del contrato (página 13): { "codigo", "mensaje", "detalles" }.
 */
public class ReglaNegocioException extends RuntimeException {

    public enum Tipo {
        SOLICITUD_INVALIDA(400), NO_AUTENTICADO(401), PROHIBIDO(403),
        NO_ENCONTRADO(404), CONFLICTO(409), REGLA_NEGOCIO(422);

        private final int http;

        Tipo(int http) { this.http = http; }

        public int http() { return http; }
    }

    private final String codigo;
    private final Tipo tipo;
    private final List<String> detalles;

    public ReglaNegocioException(Tipo tipo, String codigo, String mensaje, List<String> detalles) {
        super(mensaje);
        this.tipo = tipo;
        this.codigo = codigo;
        this.detalles = detalles == null ? List.of() : List.copyOf(detalles);
    }

    public static ReglaNegocioException solicitudInvalida(String codigo, String mensaje, String... detalles) {
        return new ReglaNegocioException(Tipo.SOLICITUD_INVALIDA, codigo, mensaje, List.of(detalles));
    }

    public static ReglaNegocioException regla(String codigo, String mensaje, String... detalles) {
        return new ReglaNegocioException(Tipo.REGLA_NEGOCIO, codigo, mensaje, List.of(detalles));
    }

    public static ReglaNegocioException conflicto(String codigo, String mensaje, String... detalles) {
        return new ReglaNegocioException(Tipo.CONFLICTO, codigo, mensaje, List.of(detalles));
    }

    public static ReglaNegocioException prohibido(String mensaje) {
        return new ReglaNegocioException(Tipo.PROHIBIDO, "ACCESO_DENEGADO", mensaje, List.of());
    }

    public static ReglaNegocioException noAutenticado(String mensaje) {
        return new ReglaNegocioException(Tipo.NO_AUTENTICADO, "NO_AUTENTICADO", mensaje, List.of());
    }

    public static ReglaNegocioException noEncontrado(String mensaje) {
        return new ReglaNegocioException(Tipo.NO_ENCONTRADO, "NO_ENCONTRADO", mensaje, List.of());
    }

    public String codigo() { return codigo; }

    public Tipo tipo() { return tipo; }

    public List<String> detalles() { return detalles; }
}
