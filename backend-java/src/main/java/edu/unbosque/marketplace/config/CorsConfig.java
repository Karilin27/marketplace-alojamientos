package edu.unbosque.marketplace.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Permite que el frontend (otro origen, p. ej. Live Server en :5500) llame a /api/**.
 * Se registra como filtro con la mayor prioridad para que también las respuestas 401 de
 * FiltroAutenticacion lleven las cabeceras CORS y el navegador pueda leerlas.
 */
@Configuration
public class CorsConfig {

    @Bean
    public FilterRegistrationBean<CorsFilter> filtroCors(@Value("${marketplace.cors-origenes}") List<String> origenes) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(origenes.stream().map(String::strip).filter(o -> !o.isEmpty()).toList());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Content-Type", "Authorization", "Accept", "Idempotency-Key"));
        cors.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/api/**", cors);

        FilterRegistrationBean<CorsFilter> registro = new FilterRegistrationBean<>(new CorsFilter(fuente));
        registro.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registro;
    }
}
