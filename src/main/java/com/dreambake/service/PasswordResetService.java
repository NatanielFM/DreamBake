package com.dreambake.service;

import com.dreambake.audit.AuditText;
import com.dreambake.config.AppProperties;
import com.dreambake.entity.AuditAction;
import com.dreambake.entity.AuditModule;
import com.dreambake.entity.AuthProvider;
import com.dreambake.entity.EmailType;
import com.dreambake.entity.PasswordResetToken;
import com.dreambake.entity.User;
import com.dreambake.repository.PasswordResetTokenRepository;
import com.dreambake.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Olvidé mi contraseña" (paso 6.1).
 *
 * Cómo funciona:
 *  1. La persona escribe su email en /recuperar. Siempre se le responde lo mismo, exista o no la cuenta,
 *     para que nadie pueda averiguar qué emails están registrados.
 *  2. Si la cuenta existe y tiene contraseña, se genera un código al azar de 256 bits (imposible de adivinar),
 *     se guarda SOLO su huella SHA-256 y se manda por email el link /recuperar/nueva?token=código.
 *     Si la cuenta entra con Google o Facebook (y no es del personal), se le manda un email explicando
 *     que entre con ese botón.
 *  3. Con el link elige la contraseña nueva. El link vence a los 30 minutos y sirve una sola vez.
 *  4. Al cambiarla: el link queda usado (y los demás links de esa persona se anulan), se registra en la
 *     auditoría y se manda el aviso "Tu contraseña fue cambiada".
 *
 * Límite: 3 pedidos por email por hora (además del límite por IP de RateLimitFilter).
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    public static final int MIN_LENGTH = 8;
    // BCrypt solo usa los primeros 72 bytes: una contraseña más larga se rechaza para no engañar a nadie
    private static final int MAX_BYTES = 72;
    public static final int VALID_MINUTES = 30;
    private static final int MAX_REQUESTS_PER_HOUR = 3;
    private static final long HOUR_MS = 60 * 60 * 1000;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm", Locale.forLanguageTag("es-AR"));

    /** Resultado de pedir el link. OK = se muestra el mensaje de siempre ("Si el email existe…"). */
    public enum RequestResult { OK, TOO_MANY, NO_MAIL }

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AuditService auditService;
    private final AppProperties appProperties;

    // Horarios de los últimos pedidos de cada email (en memoria: se reinicia con la aplicación,
    // igual que RateLimitFilter; suficiente para un negocio chico con un solo servidor)
    private final Map<String, Deque<Long>> requestsByEmail = new ConcurrentHashMap<>();

    public PasswordResetService(UserRepository userRepository, PasswordResetTokenRepository tokenRepository,
                                PasswordEncoder passwordEncoder, EmailService emailService,
                                AuditService auditService, AppProperties appProperties) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.auditService = auditService;
        this.appProperties = appProperties;
    }

    /**
     * Paso 1: alguien pidió recuperar la contraseña de este email.
     * Para no revelar si la cuenta existe, el resultado es el mismo en los dos casos (OK) y los emails
     * se mandan en segundo plano (sin esperar), así la respuesta tarda lo mismo.
     */
    @Transactional
    public RequestResult requestReset(String rawEmail) {
        // Sin cuenta de correo configurada no se puede mandar nada (esto no revela nada sobre el email)
        if (!emailService.estaConfigurado()) {
            return RequestResult.NO_MAIL;
        }
        String email = rawEmail.trim();
        // El límite cuenta para cualquier email, exista o no: así tampoco revela nada
        if (tooManyRequests(email.toLowerCase(Locale.ROOT))) {
            return RequestResult.TOO_MANY;
        }
        // De paso se borran los links vencidos hace más de un día
        tokenRepository.deleteExpiredBefore(LocalDateTime.now().minusDays(1));

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            // No existe o está deshabilitada: no se manda nada, pero la pantalla dice lo mismo
            log.info("Recuperar contraseña: pedido para un email sin cuenta activa");
            return RequestResult.OK;
        }

        if (entersOnlyWithSocial(user)) {
            // Cuenta de cliente que entra con Google o Facebook: no tiene una contraseña que recuperar
            String red = user.getAuthProvider() == AuthProvider.FACEBOOK ? "Facebook" : "Google";
            Map<String, Object> vars = new HashMap<>();
            vars.put("nombre", firstName(user.getName()));
            vars.put("red", red);
            vars.put("loginUrl", appProperties.getPublicUrl() + "/login");
            emailService.enviar(user.getEmail(), "Entrá con " + red + " a tu cuenta", "email/recuperar-social",
                    vars, EmailType.PASSWORD_RESET, null, null);
            return RequestResult.OK;
        }

        // Código al azar: 32 bytes (256 bits) en Base64 apto para direcciones web (43 caracteres)
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        PasswordResetToken reset = new PasswordResetToken();
        reset.setTokenHash(sha256(token));   // en la base va la huella, nunca el código
        reset.setUser(user);
        reset.setCreatedAt(LocalDateTime.now());
        reset.setExpiresAt(LocalDateTime.now().plusMinutes(VALID_MINUTES));
        tokenRepository.save(reset);

        Map<String, Object> vars = new HashMap<>();
        vars.put("nombre", firstName(user.getName()));
        vars.put("link", appProperties.getPublicUrl() + "/recuperar/nueva?token=" + token);
        vars.put("minutos", VALID_MINUTES);
        emailService.enviar(user.getEmail(), "Cambiá tu contraseña", "email/recuperar-contrasena",
                vars, EmailType.PASSWORD_RESET, null, null);
        return RequestResult.OK;
    }

    /** ¿El código del link sirve? (existe, no se usó, no venció y la cuenta sigue activa) */
    @Transactional(readOnly = true)
    public boolean isValid(String token) {
        return findUsable(token).isPresent();
    }

    /**
     * Paso 3: cambia la contraseña con el código del link.
     * @return null si salió bien; si no, el mensaje para mostrar en el formulario
     */
    @Transactional
    public String resetPassword(String token, String password, String confirm) {
        String problem = checkPassword(password, confirm);
        if (problem != null) return problem;

        PasswordResetToken reset = findUsable(token).orElse(null);
        if (reset == null) return INVALID_LINK;
        User user = reset.getUser();
        LocalDateTime now = LocalDateTime.now();

        // Se anulan TODOS los links de esta persona (el que se usó y cualquier otro que haya pedido).
        // Si dos pestañas mandan el formulario a la vez, la segunda no encuentra links sin usar y se corta.
        if (tokenRepository.invalidateAll(user, now) == 0) return INVALID_LINK;

        user.setPassword(passwordEncoder.encode(password));
        // Abrir el link prueba que el email es de esta persona (igual que el código de 6 números)
        user.setEmailVerified(true);
        userRepository.save(user);

        // Auditoría: todavía no hay nadie logueado, así que se indica a mano quién fue
        auditService.logAs(user.getEmail(), AuditAction.UPDATE, AuditModule.SEGURIDAD, "Usuario", user.getId(),
                "Cambió su contraseña con el link de recuperación enviado a " + AuditText.maskEmail(user.getEmail()), null);

        // Aviso por email: si no fue la persona, se entera enseguida
        Map<String, Object> vars = new HashMap<>();
        vars.put("nombre", firstName(user.getName()));
        vars.put("cuando", now.format(WHEN));
        vars.put("loginUrl", appProperties.getPublicUrl() + "/login");
        vars.put("recuperarUrl", appProperties.getPublicUrl() + "/recuperar");
        emailService.enviar(user.getEmail(), "Tu contraseña fue cambiada", "email/contrasena-cambiada",
                vars, EmailType.PASSWORD_CHANGED, null, null);
        return null;
    }

    public static final String INVALID_LINK = "El link venció o ya se usó. Pedí uno nuevo.";

    /** Reglas de la contraseña nueva. null = está bien. */
    public static String checkPassword(String password, String confirm) {
        if (password == null || password.length() < MIN_LENGTH) {
            return "La contraseña tiene que tener al menos " + MIN_LENGTH + " caracteres.";
        }
        if (password.isBlank()) {
            return "La contraseña no puede ser solo espacios.";
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            return "La contraseña es demasiado larga (hasta 72 caracteres).";
        }
        if (!password.equals(confirm)) {
            return "Las contraseñas no coinciden.";
        }
        return null;
    }

    private Optional<PasswordResetToken> findUsable(String token) {
        if (token == null || token.isBlank() || token.length() > 100) return Optional.empty();
        return tokenRepository.findByTokenHash(sha256(token.trim()))
                .filter(t -> t.isUsable(LocalDateTime.now()))
                .filter(t -> Boolean.TRUE.equals(t.getUser().getEnabled()) && !entersOnlyWithSocial(t.getUser()));
    }

    // Cliente vinculado a Google o Facebook: entra con ese botón. El personal (admin y pastelero) siempre
    // tiene contraseña, aunque haya vinculado Google desde su perfil, así que sí puede recuperarla.
    private static boolean entersOnlyWithSocial(User user) {
        return user.getAuthProvider() != null && user.getAuthProvider() != AuthProvider.LOCAL
                && !SocialLoginService.isStaff(user);
    }

    // Registra el pedido y dice si ese email ya pidió 3 links en la última hora
    private boolean tooManyRequests(String email) {
        long now = System.currentTimeMillis();
        if (requestsByEmail.size() > 10_000) {
            // Limpieza para que el mapa no crezca sin fin
            requestsByEmail.entrySet().removeIf(e -> e.getValue().isEmpty() || now - e.getValue().peekLast() > HOUR_MS);
        }
        Deque<Long> times = requestsByEmail.computeIfAbsent(email, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && now - times.peekFirst() > HOUR_MS) times.pollFirst();
            if (times.size() >= MAX_REQUESTS_PER_HOUR) return true;
            times.addLast(now);
            return false;
        }
    }

    // Huella SHA-256 en hexadecimal (64 caracteres)
    static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);   // no pasa: Java siempre lo trae
        }
    }

    private static String firstName(String name) {
        if (name == null || name.isBlank()) return "";
        return name.trim().split("\\s+")[0];
    }
}
