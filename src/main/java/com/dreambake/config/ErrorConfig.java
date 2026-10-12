package com.dreambake.config;

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.webmvc.error.DefaultErrorAttributes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;

/**
 * Qué datos lleva una respuesta de error (paso 6.3).
 *
 * Cuando algo falla, Spring Boot arma la respuesta de error (la página error/404.html… o, para el JavaScript
 * de los paneles, un JSON). Con DevTools activo (al programar) ese JSON incluía la "traza": la lista de
 * clases y líneas de código por donde pasó el error. Eso le da pistas a un atacante, así que acá se fuerza
 * SIEMPRE la versión mínima (fecha, código, nombre del error y ruta), sin traza, sin el nombre de la
 * excepción y sin el mensaje interno, sin importar la configuración.
 */
@Configuration
public class ErrorConfig {

    @Bean
    public DefaultErrorAttributes errorAttributes() {
        return new DefaultErrorAttributes() {
            @Override
            public Map<String, Object> getErrorAttributes(WebRequest request, ErrorAttributeOptions options) {
                return super.getErrorAttributes(request, ErrorAttributeOptions.defaults());
            }
        };
    }
}
