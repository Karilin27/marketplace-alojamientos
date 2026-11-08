package edu.unbosque.marketplace.logs;

/**
 * Puerto de salida hacia S6 (auditoría, HU-019). S6 vive en la misma aplicación Java (decisión D-20):
 * el módulo de auditoría implementa esta interfaz como un @Component y guarda el evento en su tabla.
 * Mientras no exista esa implementación, los eventos solo quedan en el log JSON.
 */
public interface AuditoriaPuerto {

    void registrar(String usuario, String entidad, String idEntidad, String accion, String resultado);
}
