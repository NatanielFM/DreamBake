package com.dreambake.entity;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * Códigos de seguimiento de pedidos (paso 6.5): "DB-" + 6 caracteres al azar, por ejemplo DB-7K3P9Q.
 *
 * - Se usan 31 caracteres sin los que se confunden al leer o dictar (sin 0/O, 1/I/L): 31^6 ≈ 887 millones
 *   de combinaciones. Con el límite de 10 consultas por minuto por IP, probar códigos al azar no sirve.
 * - SecureRandom (y no Random): sus números no se pueden predecir a partir de los anteriores.
 */
public final class TrackingCodes {

    private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final int LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private TrackingCodes() {}

    public static String generate() {
        StringBuilder sb = new StringBuilder("DB-");
        for (int i = 0; i < LENGTH; i++) sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return sb.toString();
    }

    /**
     * Lleva lo que escribió el cliente al formato guardado: mayúsculas, sin espacios, con "DB-".
     * Acepta "db-7k3p9q", "DB 7K3P9Q" o "7K3P9Q". null si no tiene la forma de un código.
     */
    public static String normalize(String raw) {
        if (raw == null) return null;
        String s = raw.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (s.startsWith("DB")) s = s.substring(2);
        if (s.length() != LENGTH) return null;
        for (char c : s.toCharArray()) if (ALPHABET.indexOf(c) < 0) return null;
        return "DB-" + s;
    }
}
