package com.dreambake.config;

import com.dreambake.util.ClientIpResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Límite de envíos por IP para las acciones públicas (sin iniciar sesión):
 * crear pedidos, validar/consumir cupones, registrarse, iniciar sesión y darse de baja de los emails.
 *
 * Evita que alguien llene la base de pedidos falsos, pruebe miles de códigos
 * de cupón o pruebe contraseñas en muchas cuentas con un programa. Funciona con
 * una "ventana deslizante": se guardan los horarios de los últimos envíos de cada
 * IP y, si en los últimos 10 minutos ya hubo demasiados, se responde 429
 * (Demasiadas solicitudes).
 *
 * La IP se obtiene con ClientIpResolver: detrás de Tailscale Funnel todas las conexiones
 * llegan desde 127.0.0.1, y sin él el límite sería para todos los clientes juntos.
 *
 * Los datos viven en memoria: se reinician al reiniciar la aplicación,
 * suficiente para un negocio chico con un solo servidor.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 10 * 60 * 1000;   // 10 minutos
    private static final int MAX_ORDERS = 10;                // pedidos por IP cada 10 minutos
    private static final int MAX_COUPONS = 30;               // validaciones/consumos de cupón
    private static final int MAX_REGISTERS = 10;             // cuentas nuevas
    private static final int MAX_LOGINS = 20;                // intentos de inicio de sesión (cualquier cuenta)
    private static final int MAX_UNSUBSCRIBES = 20;          // bajas de emails (botón de /baja)
    private static final int MAX_EMAIL_CODES = 15;           // "Completá tu email": pedir código o probarlo

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        Integer limit = null;
        String bucket = null;

        if ("POST".equals(method) && "/api/orders".equals(path)) { limit = MAX_ORDERS; bucket = "orders"; }
        else if (path.startsWith("/api/coupons/validate/")) { limit = MAX_COUPONS; bucket = "coupons"; }
        else if ("POST".equals(method) && "/register".equals(path)) { limit = MAX_REGISTERS; bucket = "register"; }
        // Frena a quien prueba contraseñas en muchas cuentas distintas desde la misma IP
        // (el bloqueo por cuenta de LoginAttemptService no lo detecta porque cambia de email)
        else if ("POST".equals(method) && "/login".equals(path)) { limit = MAX_LOGINS; bucket = "login"; }
        else if ("POST".equals(method) && "/baja".equals(path)) { limit = MAX_UNSUBSCRIBES; bucket = "baja"; }
        // Registro con Facebook sin email: cada envío manda un email o prueba un código
        else if ("POST".equals(method) && (path.startsWith("/login/completar-email") || path.startsWith("/profile/email/"))) {
            limit = MAX_EMAIL_CODES; bucket = "email-code";   // también "Confirmá tu email" del perfil
        }

        if (limit != null && tooMany(bucket + ":" + ClientIpResolver.resolve(request), limit)) {
            response.setStatus(429);
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write("Hiciste demasiados intentos seguidos. Esperá unos minutos y probá de nuevo.");
            return;
        }
        chain.doFilter(request, response);
    }

    // Registra el envío y dice si se pasó del límite
    private boolean tooMany(String key, int limit) {
        long now = System.currentTimeMillis();
        Deque<Long> times = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && now - times.peekFirst() > WINDOW_MS) times.pollFirst();
            if (times.size() >= limit) return true;
            times.addLast(now);
            return false;
        }
    }
}
