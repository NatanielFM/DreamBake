package com.dreambake.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuración de Bakey, el asistente de IA (paso 5.1): las claves que empiezan con "bakey."
 * en config/application.properties (ver config/application.ejemplo.properties).
 *
 *   bakey.gemini.api-key          clave de la API de Gemini (vacía = Bakey "no configurado")
 *   bakey.gemini.model            modelo a usar (por defecto gemini-3.5-flash-lite)
 *   bakey.gemini.base-url         dirección de la API (solo se cambia para probar con un Gemini simulado)
 *   bakey.gemini.timeout-seconds  cuánto se espera la respuesta antes de rendirse (30)
 *   bakey.limits.per-minute       mensajes por usuario por minuto (20)
 *   bakey.limits.per-day          mensajes por usuario por día (200)
 *
 * La clave también se puede cargar como variable de entorno GEMINI_API_KEY (el nombre que usa
 * Google en su documentación) o BAKEY_GEMINI_APIKEY (la forma estándar de Spring).
 *
 * A propósito NO tiene toString(): así la clave nunca termina impresa en un log por accidente.
 */
@Component
@ConfigurationProperties(prefix = "bakey")
public class BakeyProperties {

    private final Gemini gemini = new Gemini();
    private final Limits limits = new Limits();

    public Gemini getGemini() { return gemini; }
    public Limits getLimits() { return limits; }

    /** true si hay una clave de Gemini cargada (en el archivo o en una variable de entorno). */
    public boolean isConfigured() {
        return !gemini.getApiKey().isBlank();
    }

    public static class Gemini {
        private String apiKey = "";
        private String model = "gemini-3.5-flash-lite";
        private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";
        private int timeoutSeconds = 30;

        // Si en el archivo está vacía, se busca la variable de entorno GEMINI_API_KEY
        public String getApiKey() {
            if (apiKey != null && !apiKey.isBlank()) return apiKey.trim();
            String fromEnv = System.getenv("GEMINI_API_KEY");
            return fromEnv == null ? "" : fromEnv.trim();
        }
        public void setApiKey(String apiKey) { this.apiKey = apiKey == null ? "" : apiKey; }

        public String getModel() { return model; }
        public void setModel(String model) {
            if (model != null && !model.isBlank()) this.model = model.trim();
        }

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) {
            // Sin "/" al final, para armar la dirección con baseUrl + "/models/…"
            if (baseUrl != null && !baseUrl.isBlank()) this.baseUrl = baseUrl.trim().replaceAll("/+$", "");
        }

        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) {
            if (timeoutSeconds > 0) this.timeoutSeconds = timeoutSeconds;
        }
    }

    // Límite propio por usuario, para no gastar la cuota gratuita de Gemini (ver BakeyRateLimiter)
    public static class Limits {
        private int perMinute = 20;
        private int perDay = 200;

        public int getPerMinute() { return perMinute; }
        public void setPerMinute(int perMinute) { if (perMinute > 0) this.perMinute = perMinute; }

        public int getPerDay() { return perDay; }
        public void setPerDay(int perDay) { if (perDay > 0) this.perDay = perDay; }
    }
}
