package com.dreambake.service;

import com.dreambake.entity.AuthProvider;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;

/**
 * Registro con red social que quedó a medias porque la red social no mandó el email
 * (pasa con Facebook cuando la persona se registró en Facebook con su teléfono).
 *
 * Se guarda en la sesión (del lado del servidor, el navegador no lo ve) mientras la persona
 * escribe su email y el código de 6 dígitos que le mandamos para comprobar que es suyo.
 * Recién cuando el código es correcto se crea la cuenta (SocialSignupController).
 */
public class PendingSocialSignup implements Serializable {

    public static final String SESSION_KEY = "oauth2.pendingSignup";

    // Todo el trámite tiene que terminarse en 30 minutos; cada código vale 15 minutos
    private static final Duration MAX_AGE = Duration.ofMinutes(30);
    public static final Duration CODE_VALIDITY = Duration.ofMinutes(15);
    // Límites para que nadie pruebe códigos al azar ni use el sitio para mandar emails sin parar
    public static final int MAX_CODE_ATTEMPTS = 5;
    public static final int MAX_SENDS = 5;
    public static final Duration RESEND_WAIT = Duration.ofSeconds(60);

    private final AuthProvider provider;
    private final String providerId;
    private final String name;
    private final String pictureUrl;
    private final Instant createdAt = Instant.now();

    // Paso 2: email escrito y código enviado
    private String email;
    private String code;
    private Instant codeSentAt;
    private int codeAttempts;
    private int sends;

    public PendingSocialSignup(AuthProvider provider, String providerId, String name, String pictureUrl) {
        this.provider = provider;
        this.providerId = providerId;
        this.name = name;
        this.pictureUrl = pictureUrl;
    }

    public boolean isExpired() {
        return createdAt.plus(MAX_AGE).isBefore(Instant.now());
    }

    /** ¿Ya mandamos un código que todavía sirve? (paso 2 de la pantalla) */
    public boolean hasActiveCode() {
        return code != null && codeSentAt != null && codeSentAt.plus(CODE_VALIDITY).isAfter(Instant.now())
                && codeAttempts < MAX_CODE_ATTEMPTS;
    }

    /** Segundos que faltan para poder pedir otro código (0 = ya se puede). */
    public long secondsUntilResend() {
        if (codeSentAt == null) return 0;
        long left = Duration.between(Instant.now(), codeSentAt.plus(RESEND_WAIT)).getSeconds();
        return Math.max(0, left);
    }

    public void codeSent(String email, String code) {
        this.email = email;
        this.code = code;
        this.codeSentAt = Instant.now();
        this.codeAttempts = 0;
        this.sends++;
    }

    public void forgetCode() {
        this.code = null;
    }

    public void countAttempt() {
        this.codeAttempts++;
    }

    public AuthProvider getProvider() { return provider; }
    public String getProviderId() { return providerId; }
    public String getName() { return name; }
    public String getPictureUrl() { return pictureUrl; }
    public String getEmail() { return email; }
    public String getCode() { return code; }
    public int getSends() { return sends; }
    public int getCodeAttempts() { return codeAttempts; }
}
