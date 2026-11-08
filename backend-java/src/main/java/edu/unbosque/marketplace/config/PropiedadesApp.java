package edu.unbosque.marketplace.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Configuración propia del proyecto (prefijo "marketplace" en application.yml / variables de entorno). */
@ConfigurationProperties(prefix = "marketplace")
public record PropiedadesApp(
        /** Secreto HS256 con el que S1 firma el JWT (mismo valor en todos los servicios). */
        String jwtSecret,
        /** Secreto para firmar el token de cotización (RN-08). */
        String cotizacionSecret,
        /** Se usa solo si S2 no tiene el parámetro tarifa_servicio_pct (RN-17). */
        @DefaultValue("10") BigDecimal tarifaServicioPctPorDefecto,
        /** Minutos que se retiene el periodo mientras se paga (RN-10). */
        @DefaultValue("15") int retencionPagoMinutos,
        /** simulada | stripe. */
        @DefaultValue("simulada") String pasarela,
        String stripeApiKey,
        @DefaultValue("cop") String moneda,
        @DefaultValue("America/Bogota") String zona) {
}
