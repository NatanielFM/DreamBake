package com.dreambake.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bloqueo temporal del login por intentos fallidos.
 *
 * Si alguien se equivoca de contraseña 5 veces seguidas con el mismo email,
 * esa cuenta queda bloqueada 15 minutos. Así no se puede adivinar una
 * contraseña probando miles de combinaciones.
 * Un login correcto borra el contador. Los datos viven en memoria.
 */
@Service
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final long BLOCK_MS = 15 * 60 * 1000;   // 15 minutos

    private record Attempts(int count, long lastFailure) {}

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    public void loginFailed(String email) {
        if (email == null) return;
        String key = email.trim().toLowerCase();
        attempts.merge(key, new Attempts(1, System.currentTimeMillis()),
                (old, n) -> new Attempts(isExpired(old) ? 1 : old.count() + 1, n.lastFailure()));
    }

    public void loginSucceeded(String email) {
        if (email != null) attempts.remove(email.trim().toLowerCase());
    }

    public boolean isBlocked(String email) {
        if (email == null) return false;
        Attempts a = attempts.get(email.trim().toLowerCase());
        if (a == null) return false;
        if (isExpired(a)) {
            attempts.remove(email.trim().toLowerCase());
            return false;
        }
        return a.count() >= MAX_ATTEMPTS;
    }

    private boolean isExpired(Attempts a) {
        return System.currentTimeMillis() - a.lastFailure() > BLOCK_MS;
    }
}
