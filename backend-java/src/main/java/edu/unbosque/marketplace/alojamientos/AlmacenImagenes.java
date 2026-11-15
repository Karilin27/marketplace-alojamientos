package edu.unbosque.marketplace.alojamientos;

/** Puerto de almacenamiento de imágenes (D-21). La implementación usa Supabase Storage; las pruebas usan un simulado. */
public interface AlmacenImagenes {

    /** true si tiene la configuración necesaria (URL y llave) para subir archivos. */
    boolean configurado();

    /**
     * Guarda el archivo en la ruta indicada y devuelve su URL pública.
     *
     * @throws edu.unbosque.marketplace.common.ReglaNegocioException si el almacenamiento rechaza el archivo o no responde
     */
    String guardar(String ruta, byte[] contenido, String tipoMime);
}
