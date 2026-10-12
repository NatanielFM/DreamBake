package com.dreambake.service;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;

/**
 * Un código de 6 números mandado por email para comprobar que ese email es de quien lo escribió.
 *
 * Se guarda en la sesión (del lado del servidor; el navegador no lo ve). Lo usan:
 *  - el registro con Facebook cuando Facebook no manda el email (PendingSocialSignup, paso 2.3),
 *  - "Confirmá tu email" del perfil, para las cuentas creadas con email y contraseña (paso 2.4).
 * El envío lo hace EmailCodeService.
 */
public class EmailCodeChallenge implements Serializable {

    // Cada código vale 15 minutos
    public static final Duration CODE_VALIDITY = Duration.ofMinutes(15);
    // Límites para que nadie pruebe códigos al azar ni use el sitio para mandar emails sin parar
    public static final int MAX_CODE_ATTEMPTS = 5;
    public static final int MAX_SENDS = 5;
    public static final Duration RESEND_WAIT = Duration.ofSeconds(60);

    private String email;
    private String code;
    private Instant codeSentAt;
    private int codeAttempts;
    private int sends;

    /** ¿Hay un código mandado que todavía sirve? */
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

    /**
     * ¿El código escrito es el correcto? Si no, cuenta el intento.
     * La comparación es "de tiempo constante": no da pistas de cuántos números se acertaron.
     */
    public boolean checkCode(String typed) {
        if (!hasActiveCode()) return false;
        String clean = typed == null ? "" : typed.replaceAll("\\s", "");
        boolean ok = MessageDigest.isEqual(clean.getBytes(StandardCharsets.UTF_8), code.getBytes(StandardCharsets.UTF_8));
        if (!ok) codeAttempts++;
        return ok;
    }

    public int attemptsLeft() {
        return Math.max(0, MAX_CODE_ATTEMPTS - codeAttempts);
    }

    void codeSent(String email, String code) {
        this.email = email;
        this.code = code;
        this.codeSentAt = Instant.now();
        this.codeAttempts = 0;
        this.sends++;
    }

    public void forgetCode() {
        this.code = null;
    }

    public String getEmail() { return email; }
    public int getSends() { return sends; }
}
