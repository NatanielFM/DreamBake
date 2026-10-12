package com.dreambake.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Mientras MaintenanceMode está activo (se está restaurando una copia de seguridad, paso 4.3),
 * responde 503 "En mantenimiento" a todos los pedidos, salvo los archivos de diseño (CSS, JS,
 * imágenes). La restauración en sí ya entró antes de que se active, así que no la frena.
 *
 * Como RateLimitFilter, no es un @Component: lo crea SecurityConfig y lo pone primero en la cadena.
 */
public class MaintenanceFilter extends OncePerRequestFilter {

    private final MaintenanceMode maintenanceMode;

    public MaintenanceFilter(MaintenanceMode maintenanceMode) {
        this.maintenanceMode = maintenanceMode;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean asset = path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/img/");
        if (!maintenanceMode.isActive() || asset) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setHeader("Retry-After", "60");
        if (path.startsWith("/api/")) {
            // El JS del panel muestra data.message (o data.error) en sus avisos
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"El sistema está en mantenimiento (se está restaurando una copia "
                    + "de seguridad). Probá de nuevo en un minuto.\"}");
        } else {
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().write("<!DOCTYPE html><html lang=\"es\"><head><meta charset=\"utf-8\">"
                    + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                    + "<title>En mantenimiento</title></head><body style=\"font-family:sans-serif;padding:2rem;"
                    + "max-width:36rem;margin:auto;line-height:1.5\"><h1>En mantenimiento</h1>"
                    + "<p>Estamos restaurando una copia de seguridad. Probá de nuevo en un minuto.</p></body></html>");
        }
    }
}
