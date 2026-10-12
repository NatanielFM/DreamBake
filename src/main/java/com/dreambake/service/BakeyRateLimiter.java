package com.dreambake.service;

import com.dreambake.config.BakeyProperties;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Límite propio de mensajes a Bakey por usuario (paso 5.1).
 *
 * El plan gratuito de Gemini tiene un cupo de pedidos por minuto y por día para TODO el negocio.
 * Si una sola persona manda muchos mensajes seguidos (o un programa lo hace con su sesión),
 * dejaría a los demás sin asistente. Por eso cada usuario tiene su propio tope
 * (bakey.limits.per-minute y bakey.limits.per-day, por defecto 20 y 200).
 *
 * Cómo cuenta:
 *  - Por minuto: "ventana deslizante", igual que RateLimitFilter: se guardan los horarios de los
 *    últimos mensajes y se cuentan los de los últimos 60 segundos.
 *  - Por día: un contador que vuelve a cero cuando cambia la fecha (hora de Argentina).
 * Solo se cuentan los mensajes que se dejaron pasar. Los datos viven en memoria (se reinician
 * al reiniciar la app), suficiente para un negocio chico con un solo servidor.
 */
@Service
public class BakeyRateLimiter {

    private static final long MINUTE_MS = 60_000;
    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    /** Resultado de pedir permiso para mandar un mensaje. */
    public enum Result { ALLOWED, MINUTE_LIMIT, DAY_LIMIT }

    // Mensajes de un usuario: horarios del último minuto + cuántos lleva en el día
    private static class Usage {
        final Deque<Long> lastMinute = new ArrayDeque<>();
        LocalDate day;
        int countToday;
    }

    private final Map<String, Usage> usage = new ConcurrentHashMap<>();
    private final BakeyProperties properties;

    public BakeyRateLimiter(BakeyProperties properties) {
        this.properties = properties;
    }

    /** Si el usuario todavía tiene cupo, lo anota y devuelve ALLOWED; si no, dice qué límite alcanzó. */
    public Result tryAcquire(String email) {
        String key = email == null ? "" : email.trim().toLowerCase();
        Usage u = usage.computeIfAbsent(key, k -> new Usage());
        long now = System.currentTimeMillis();
        LocalDate today = LocalDate.now(ZONE);

        synchronized (u) {
            // Día nuevo: el contador diario vuelve a cero
            if (!today.equals(u.day)) {
                u.day = today;
                u.countToday = 0;
            }
            while (!u.lastMinute.isEmpty() && now - u.lastMinute.peekFirst() >= MINUTE_MS) {
                u.lastMinute.pollFirst();
            }
            if (u.countToday >= properties.getLimits().getPerDay()) return Result.DAY_LIMIT;
            if (u.lastMinute.size() >= properties.getLimits().getPerMinute()) return Result.MINUTE_LIMIT;

            u.lastMinute.addLast(now);
            u.countToday++;
            return Result.ALLOWED;
        }
    }
}
