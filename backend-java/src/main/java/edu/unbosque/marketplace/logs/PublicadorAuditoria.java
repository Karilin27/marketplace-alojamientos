package edu.unbosque.marketplace.logs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Entrega los eventos de auditoría al módulo S6 (HU-019) a través de {@link AuditoriaPuerto}.
 * Si el módulo S6 aún no está desplegado, no hace nada (el evento ya quedó en el log).
 * Un fallo de la auditoría nunca hace fallar la operación de negocio.
 */
@Component
public class PublicadorAuditoria {

    private static final Logger LOG = LoggerFactory.getLogger(PublicadorAuditoria.class);

    private final ObjectProvider<AuditoriaPuerto> puerto;

    public PublicadorAuditoria(ObjectProvider<AuditoriaPuerto> puerto) {
        this.puerto = puerto;
    }

    public void publicar(String usuario, String entidad, String idEntidad, String accion, String resultado) {
        AuditoriaPuerto destino = puerto.getIfAvailable();
        if (destino == null) {
            return;
        }
        try {
            destino.registrar(usuario, entidad, idEntidad, accion, resultado);
        } catch (RuntimeException e) {
            LOG.warn("No se pudo registrar el evento de auditoría {} ({})", accion, e.getClass().getSimpleName());
        }
    }
}
