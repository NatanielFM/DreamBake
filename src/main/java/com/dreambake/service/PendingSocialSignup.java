package com.dreambake.service;

import com.dreambake.entity.AuthProvider;

import java.time.Duration;
import java.time.Instant;

/**
 * Registro con red social que quedó a medias porque la red social no mandó el email
 * (pasa con Facebook cuando la persona se registró en Facebook con su teléfono).
 *
 * Se guarda en la sesión (del lado del servidor, el navegador no lo ve) mientras la persona
 * escribe su email y el código de 6 dígitos que le mandamos para comprobar que es suyo
 * (esa parte la hereda de EmailCodeChallenge).
 * Recién cuando el código es correcto se crea la cuenta (SocialSignupController).
 */
public class PendingSocialSignup extends EmailCodeChallenge {

    public static final String SESSION_KEY = "oauth2.pendingSignup";

    // Todo el trámite tiene que terminarse en 30 minutos
    private static final Duration MAX_AGE = Duration.ofMinutes(30);

    private final AuthProvider provider;
    private final String providerId;
    private final String name;
    private final String pictureUrl;
    private final Instant createdAt = Instant.now();

    public PendingSocialSignup(AuthProvider provider, String providerId, String name, String pictureUrl) {
        this.provider = provider;
        this.providerId = providerId;
        this.name = name;
        this.pictureUrl = pictureUrl;
    }

    public boolean isExpired() {
        return createdAt.plus(MAX_AGE).isBefore(Instant.now());
    }

    public AuthProvider getProvider() { return provider; }
    public String getProviderId() { return providerId; }
    public String getName() { return name; }
    public String getPictureUrl() { return pictureUrl; }
}
