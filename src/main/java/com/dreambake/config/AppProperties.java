package com.dreambake.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Datos de configuración propios de DreamBake: las claves que empiezan con "app."
 * en config/application.properties (ver config/application.ejemplo.properties).
 *
 * Spring completa esta clase solo al arrancar: "app.public-url" va a publicUrl,
 * "app.business.name" va a business.name, etc. (la regla es que los guiones se
 * convierten en mayúsculas). Desde cualquier clase se usa así:
 *
 *     @Autowired AppProperties app;
 *     String link = app.getPublicUrl() + "/login";
 *
 * Si falta una clave se usa el valor por defecto de acá abajo, así la app nunca
 * falla al arrancar por un dato que no se cargó.
 */
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    // Dirección pública del sitio (sin "/" al final). Se usa en los links de los mails.
    private String publicUrl = "http://localhost:8080";

    private final Business business = new Business();

    public String getPublicUrl() {
        return publicUrl;
    }

    public void setPublicUrl(String publicUrl) {
        // Se le saca la "/" del final para poder armar links con publicUrl + "/ruta"
        this.publicUrl = (publicUrl == null || publicUrl.isBlank())
                ? "http://localhost:8080"
                : publicUrl.trim().replaceAll("/+$", "");
    }

    public Business getBusiness() {
        return business;
    }

    // Datos del negocio: app.business.name, app.business.whatsapp, app.business.email
    public static class Business {
        private String name = "Dulce Sueños Posadas";
        private String whatsapp = "";
        private String email = "";

        public String getName() { return name; }
        public void setName(String name) {
            if (name != null && !name.isBlank()) this.name = name.trim();
        }

        // Con código de país y sin signos (ej: 5493764123456). Vacío = no configurado.
        public String getWhatsapp() { return whatsapp; }
        public void setWhatsapp(String whatsapp) { this.whatsapp = whatsapp == null ? "" : whatsapp.trim(); }

        // Vacío = no configurado.
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email == null ? "" : email.trim(); }
    }
}
