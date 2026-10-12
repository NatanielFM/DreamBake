package com.dreambake.service;

import com.dreambake.entity.AuditAction;
import com.dreambake.entity.AuditModule;
import com.dreambake.entity.AuthProvider;
import com.dreambake.entity.User;
import com.dreambake.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Reglas para entrar con una red social (Google ahora, Facebook en el paso 2.3).
 *
 * Recibe los datos que mandó la red social y decide a qué usuario de DreamBake corresponden:
 *  - Modo "vincular": un empleado que ya entró con contraseña tocó "Vincular cuenta de Google" en su perfil.
 *  - a. Ya hay un usuario con ese id de la red social → es él.
 *  - b. Hay un usuario con ese email → solo se vincula solo si es cliente y la red social confirma
 *       que el email es suyo (verificado). El personal (ADMIN/PASTELERO) se rechaza: tiene que
 *       entrar con contraseña y vincular desde su perfil.
 *  - c. No existe → se crea una cuenta de cliente nueva.
 *       Si la red social no mandó el email (Facebook, cuenta creada con teléfono), la cuenta NO se crea
 *       acá: se guarda el registro a medias en la sesión y se pide el email en /login/completar-email,
 *       con un código de verificación (SocialSignupController).
 *  Solo se puede vincular desde el perfil una cuenta de Google: con Facebook nunca se vincula una
 *  cuenta del personal.
 *
 * Si algo no se permite, se lanza OAuth2AuthenticationException con un código corto
 * (ver los códigos abajo); SecurityConfig lo convierte en un mensaje amable en español.
 */
@Service
public class SocialLoginService {

    // Códigos de error (SecurityConfig.oauth2FailureHandler solo acepta estos)
    public static final String ERROR_STAFF_PASSWORD = "staff_password";
    public static final String ERROR_EMAIL_UNVERIFIED = "email_unverified";
    public static final String ERROR_DISABLED = "disabled";
    public static final String ERROR_ALREADY_LINKED = "already_linked";
    public static final String ERROR_FAILED = "failed";
    // No es un error para el usuario: falta el email y hay que pedírselo (ver SecurityConfig)
    public static final String ERROR_EMAIL_REQUIRED = "email_required";

    // Datos que guarda ProfileController en la sesión cuando un empleado toca "Vincular"
    public static final String SESSION_LINK_USER_ID = "oauth2.linkUserId";
    public static final String SESSION_LINK_STARTED_AT = "oauth2.linkStartedAt";
    // Con qué red social se pidió vincular (hoy solo GOOGLE)
    public static final String SESSION_LINK_PROVIDER = "oauth2.linkProvider";
    // La sesión "con contraseña" del empleado, para devolvérsela después de pasar por Google
    public static final String SESSION_LINK_PREVIOUS_AUTH = "oauth2.linkPreviousAuth";
    // Marca que deja este servicio cuando la vinculación salió bien (la lee el successHandler)
    public static final String SESSION_LINK_DONE = "oauth2.linkDone";
    // El pedido de vincular vence a los 10 minutos (si el empleado abandona la pantalla de Google)
    private static final Duration LINK_TIMEOUT = Duration.ofMinutes(10);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    public User resolve(AuthProvider provider, String providerId, String email, boolean emailVerified,
                        String name, String pictureUrl) {
        if (providerId == null || providerId.isBlank()) {
            throw error(ERROR_FAILED);
        }

        // Modo vincular: la cuenta de la red social se une al empleado que lo pidió desde su perfil.
        // Solo si el pedido fue para ESTA red social (con Facebook nunca se vincula al personal).
        Long linkUserId = pendingLinkUserId(provider);
        if (linkUserId != null) {
            return linkToExistingUser(linkUserId, provider, providerId, pictureUrl);
        }

        // a. Ya está vinculado: entra
        Optional<User> byProvider = userRepository.findByAuthProviderAndProviderId(provider, providerId);
        if (byProvider.isPresent()) {
            User user = byProvider.get();
            checkEnabled(user);
            if (pictureUrl != null) user.setAvatarUrl(pictureUrl); // la foto puede haber cambiado
            // Si la red social confirma justo el email de la cuenta, queda comprobado
            if (emailVerified && email != null && email.trim().equalsIgnoreCase(user.getEmail())) {
                user.setEmailVerified(true);
            }
            return userRepository.save(user);
        }

        // Sin email (Facebook con cuenta creada por teléfono): se guarda lo que sabemos y se pide el email
        if (email == null || email.isBlank()) {
            HttpSession session = currentSession(true);
            if (session == null) throw error(ERROR_FAILED);
            session.setAttribute(PendingSocialSignup.SESSION_KEY,
                    new PendingSocialSignup(provider, providerId, name, pictureUrl));
            throw error(ERROR_EMAIL_REQUIRED);
        }
        String cleanEmail = email.trim();

        // b. Ya existe una cuenta con ese email
        Optional<User> byEmail = userRepository.findByEmail(cleanEmail);
        if (byEmail.isPresent()) {
            User user = byEmail.get();
            // El personal nunca se vincula solo: si alguien se hace con su Gmail, entraría al panel
            if (isStaff(user)) {
                throw error(ERROR_STAFF_PASSWORD);
            }
            // Solo si Google confirma que el email es de esa persona
            if (!emailVerified) {
                throw error(ERROR_EMAIL_UNVERIFIED);
            }
            checkEnabled(user);
            // Si esa cuenta se había creado con contraseña pero el email nunca se comprobó, la contraseña
            // podría ser de otra persona que se registró con este email antes que el dueño. Como la red
            // social acaba de probar que el email es de quien está entrando, esa contraseña se anula
            // (se reemplaza por una al azar que nadie conoce): desde ahora entra con la red social.
            if (!Boolean.TRUE.equals(user.getEmailVerified())) {
                user.setPassword(passwordEncoder.encode(java.util.UUID.randomUUID().toString()));
            }
            // Si ya estaba comprobado, la contraseña que tenía no se toca: puede entrar de las dos formas
            user.setEmailVerified(true);
            user.setAuthProvider(provider);
            user.setProviderId(providerId);
            if (pictureUrl != null) user.setAvatarUrl(pictureUrl);
            User saved = userRepository.save(user);
            auditService.logAs(saved.getEmail(), AuditAction.UPDATE, AuditModule.SEGURIDAD, "Usuario", saved.getId(),
                    "Se vinculó su cuenta de " + networkName(provider) + " porque tiene el mismo email confirmado", null);
            return saved;
        }

        // c. Cuenta nueva de cliente. También se pide el email verificado: así nadie puede
        // crear una cuenta con un email ajeno (con Gmail siempre viene verificado).
        if (!emailVerified) {
            throw error(ERROR_EMAIL_UNVERIFIED);
        }
        String displayName = (name != null && !name.isBlank()) ? name.trim() : cleanEmail.split("@")[0];
        return userService.createSocialUser(cleanEmail, displayName, provider, providerId, pictureUrl);
    }

    /**
     * Crea la cuenta de un registro a medias, cuando la persona ya confirmó su email con el código
     * (lo llama SocialSignupController). Se vuelve a revisar que nadie haya usado ese email
     * o esa cuenta de la red social mientras tanto.
     */
    public User completePendingSignup(PendingSocialSignup pending) {
        String email = pending.getEmail().trim();
        if (userRepository.findByEmail(email).isPresent()) {
            throw new IllegalStateException("Ya existe una cuenta con ese email");
        }
        if (userRepository.findByAuthProviderAndProviderId(pending.getProvider(), pending.getProviderId()).isPresent()) {
            throw new IllegalStateException("Esa cuenta de la red social ya está vinculada");
        }
        String name = pending.getName();
        String displayName = (name != null && !name.isBlank()) ? name.trim() : email.split("@")[0];
        return userService.createSocialUser(email, displayName, pending.getProvider(),
                pending.getProviderId(), pending.getPictureUrl());
    }

    private static String networkName(AuthProvider provider) {
        return provider == AuthProvider.FACEBOOK ? "Facebook" : "Google";
    }

    /** ¿Es del personal (administrador o pastelero)? */
    public static boolean isStaff(User user) {
        String roles = user.getRoles() == null ? "" : user.getRoles();
        return roles.contains("ADMIN") || roles.contains("PASTELERO");
    }

    private User linkToExistingUser(Long userId, AuthProvider provider, String providerId, String pictureUrl) {
        User user = userRepository.findById(userId).orElseThrow(() -> error(ERROR_FAILED));
        // Esa cuenta de Google ya pertenece a otro usuario: no se puede usar para dos cuentas
        Optional<User> owner = userRepository.findByAuthProviderAndProviderId(provider, providerId);
        if (owner.isPresent() && !owner.get().getId().equals(user.getId())) {
            throw error(ERROR_ALREADY_LINKED);
        }
        checkEnabled(user);
        user.setAuthProvider(provider);
        user.setProviderId(providerId);
        if (pictureUrl != null) user.setAvatarUrl(pictureUrl);
        User saved = userRepository.save(user);
        auditService.logAs(saved.getEmail(), AuditAction.UPDATE, AuditModule.SEGURIDAD, "Usuario", saved.getId(),
                "Vinculó su cuenta de " + networkName(provider) + " desde su perfil", null);
        HttpSession session = currentSession(false);
        if (session != null) session.setAttribute(SESSION_LINK_DONE, Boolean.TRUE);
        return saved;
    }

    // Devuelve el id del empleado que pidió vincular, si el pedido sigue vigente y es para esta red social
    private Long pendingLinkUserId(AuthProvider provider) {
        HttpSession session = currentSession(false);
        if (session == null) return null;
        if (session.getAttribute(SESSION_LINK_PROVIDER) != provider) return null;
        Object id = session.getAttribute(SESSION_LINK_USER_ID);
        Object startedAt = session.getAttribute(SESSION_LINK_STARTED_AT);
        if (!(id instanceof Long userId) || !(startedAt instanceof Instant started)) return null;
        if (started.plus(LINK_TIMEOUT).isBefore(Instant.now())) {
            clearLinkRequest(session);
            return null;
        }
        return userId;
    }

    /** Borra de la sesión el pedido de vincular (lo usan también los handlers de SecurityConfig). */
    public static void clearLinkRequest(HttpSession session) {
        if (session == null) return;
        session.removeAttribute(SESSION_LINK_USER_ID);
        session.removeAttribute(SESSION_LINK_STARTED_AT);
        session.removeAttribute(SESSION_LINK_PROVIDER);
        session.removeAttribute(SESSION_LINK_PREVIOUS_AUTH);
    }

    private void checkEnabled(User user) {
        if (Boolean.FALSE.equals(user.getEnabled())) {
            throw error(ERROR_DISABLED);
        }
    }

    // Igual que en UserDetailsServiceImpl: se le pide a Spring el pedido HTTP en curso
    private HttpSession currentSession(boolean create) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest().getSession(create);
        }
        return null;
    }

    private static OAuth2AuthenticationException error(String code) {
        return new OAuth2AuthenticationException(new OAuth2Error(code));
    }
}
