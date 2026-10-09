package com.dreambake.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bloqueo temporal del login por intentos fallidos.
 *
 * Si alguien se equivoca de contraseña 5 veces seguidas con el mismo email
 * DESDE LA MISMA IP, esa combinación queda bloqueada 15 minutos. Así no se
 * puede adivinar una contraseña probando miles de combinaciones.
 *
 * Se cuenta por email + IP (y no solo por email) para que nadie pueda dejar
 * a la dueña sin poder entrar escribiendo mal su contraseña a propósito:
 * el bloqueo afecta solo a la IP del que se equivoca, no a la cuenta entera.
 * A quien prueba muchas cuentas desde una IP lo frena RateLimitFilter
 * (límite de envíos a /login por IP).
 *
 * Un login correcto borra el contador. Los datos viven en memoria.
 */
@Service
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final long BLOCK_MS = 15 * 60 * 1000;   // 15 minutos

    private record Attempts(int count, long lastFailure) {}

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    public void loginFailed(String email, String ip) {
        if (email == null) return;
        attempts.merge(key(email, ip), new Attempts(1, System.currentTimeMillis()),
                (old, n) -> new Attempts(isExpired(old) ? 1 : old.count() + 1, n.lastFailure()));
    }

    public void loginSucceeded(String email, String ip) {
        if (email != null) attempts.remove(key(email, ip));
    }

    public boolean isBlocked(String email, String ip) {
        if (email == null) return false;
        String key = key(email, ip);
        Attempts a = attempts.get(key);
        if (a == null) return false;
        if (isExpired(a)) {
            attempts.remove(key);
            return false;
        }
        return a.count() >= MAX_ATTEMPTS;
    }

    // Clave del contador: "email|ip" (el email en minúsculas, sin espacios)
    private String key(String email, String ip) {
        return email.trim().toLowerCase() + "|" + ip;
    }

    private boolean isExpired(Attempts a) {
        return System.currentTimeMillis() - a.lastFailure() > BLOCK_MS;
    }
}
