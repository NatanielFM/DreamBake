package com.dreambake.service;

import com.dreambake.entity.EmailType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Manda el código de 6 números para confirmar un email (ver EmailCodeChallenge).
 * Lo usan SocialSignupController (Facebook sin email) y ProfileController ("Confirmá tu email").
 */
@Service
public class EmailCodeService {

    private static final Logger log = LoggerFactory.getLogger(EmailCodeService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    // Resultado especial: no hay cuenta de correo configurada (la pantalla muestra un aviso con link)
    public static final String NO_MAIL = "nomail";

    @Autowired
    private EmailService emailService;

    /**
     * Genera un código nuevo y lo manda a ese email.
     *
     * @param purpose para qué es el código; se muestra en el email ("crear tu cuenta", "confirmar tu email")
     * @return null si salió bien; si no, el mensaje para mostrar (o NO_MAIL)
     */
    public String send(EmailCodeChallenge challenge, String email, String firstName, String purpose) {
        if (challenge.getSends() >= EmailCodeChallenge.MAX_SENDS) {
            return "Pediste demasiados códigos. Probá de nuevo más tarde.";
        }
        long wait = challenge.secondsUntilResend();
        if (wait > 0) {
            return "Esperá " + wait + " segundos para pedir otro código.";
        }
        if (!emailService.estaConfigurado()) {
            return NO_MAIL;
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        boolean sent;
        try {
            // Se espera el resultado (hasta 20 s) para poder avisar si el email no salió
            sent = emailService.enviar(email, "Tu código de " + purpose, "email/codigo",
                            Map.of("codigo", code, "nombre", firstName == null ? "" : firstName, "motivo", purpose),
                            EmailType.ACCOUNT_VERIFICATION, null, null)
                    .get(20, TimeUnit.SECONDS).ok();
        } catch (Exception e) {
            log.warn("No se pudo mandar el código de verificación a {}: {}", email, e.getMessage());
            sent = false;
        }
        if (!sent) {
            return "No pudimos mandarte el email. Revisá la dirección o probá en unos minutos.";
        }
        challenge.codeSent(email, code);
        return null;
    }
}
