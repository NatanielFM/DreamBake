package com.dreambake.config;

import org.apache.catalina.valves.RemoteIpValve;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;

/**
 * La app detrás del proxy de Tailscale Funnel.
 *
 * Tailscale recibe las visitas de internet por HTTPS y se las pasa a la app por HTTP
 * desde la misma PC (127.0.0.1). Sin esta configuración la app creía que la visita
 * había llegado por "http://", y al redirigir (por ejemplo, después de un login fallido
 * a /login?error) mandaba al navegador a http://… El navegador lo veía como otro sitio
 * y la política de seguridad (CSP form-action 'self') bloqueaba el formulario.
 *
 * El proxy avisa cómo llegó la visita en dos encabezados:
 *  - X-Forwarded-Proto: "https"  → la app arma sus direcciones con https://
 *  - X-Forwarded-For: IP real    → la app ve la IP del visitante, no 127.0.0.1
 *
 * Esos encabezados los puede escribir cualquiera, así que el RemoteIpValve de Tomcat
 * SOLO les cree si la conexión viene de la misma PC (127.x.x.x o ::1), que es donde corre
 * Tailscale. Si alguien se conecta directo desde afuera, se ignoran. De la lista de
 * X-Forwarded-For toma la última IP que no sea de la propia PC (la primera la puede inventar
 * el visitante). ClientIpResolver aplica la misma regla.
 */
@Configuration
public class ProxyConfig implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    // Solo la propia PC: 127.0.0.0/8 y ::1 (en sus dos formas de escritura)
    private static final String LOCAL_PROXY = "127\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|0:0:0:0:0:0:0:1|::1";

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        RemoteIpValve valve = new RemoteIpValve();
        valve.setInternalProxies(LOCAL_PROXY);
        valve.setTrustedProxies("");                    // ningún otro proxy de confianza
        valve.setRemoteIpHeader("X-Forwarded-For");
        valve.setProtocolHeader("X-Forwarded-Proto");
        valve.setProtocolHeaderHttpsValue("https");     // con "https" también usa el puerto 443
        factory.addEngineValves(valve);
    }
}
