package com.dreambake.controller;

import com.dreambake.entity.EmailType;
import com.dreambake.entity.User;
import com.dreambake.repository.UserRepository;
import com.dreambake.service.EmailService;
import com.dreambake.service.PendingSocialSignup;
import com.dreambake.service.SocialLoginService;
import com.dreambake.service.SocialUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * "Completá tu email": cuando alguien entra con Facebook y Facebook no manda el email
 * (la cuenta de Facebook se creó con un teléfono).
 *
 * Paso 1: la persona escribe su email y le mandamos un código de 6 dígitos.
 * Paso 2: escribe el código. Si es correcto, se crea la cuenta de cliente y queda logueada.
 *
 * ¿Por qué el código? Porque un email escrito a mano no está comprobado: sin el código, alguien
 * podría crear una cuenta con el email de otra persona.
 * Si ese email ya tiene cuenta, NO se vincula (no sabemos si es de quien lo escribe):
 * se le pide que entre con su contraseña o con Google.
 */
@Controller
@RequestMapping("/login/completar-email")
public class SocialSignupController {

    private static final Logger log = LoggerFactory.getLogger(SocialSignupController.class);

    // Mismo formato de email que se valida en el navegador (form-helpers.js)
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SocialLoginService socialLoginService;

    @Autowired
    private EmailService emailService;

    // El mismo successHandler de SecurityConfig: decide a dónde va según el rol (un cliente → inicio)
    @Autowired
    private AuthenticationSuccessHandler successHandler;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @GetMapping
    public String page(HttpSession session, Model model) {
        PendingSocialSignup pending = pending(session);
        if (pending == null) {
            return "redirect:/login?social=expired";
        }
        fillModel(pending, model);
        return "completar-email";
    }

    // Paso 1: recibe el email y manda el código
    @PostMapping
    public String sendCode(@RequestParam("email") String rawEmail, HttpSession session, Model model) {
        PendingSocialSignup pending = pending(session);
        if (pending == null) {
            return "redirect:/login?social=expired";
        }
        String email = rawEmail == null ? "" : rawEmail.trim();
        model.addAttribute("typedEmail", email);

        if (email.length() > 120 || !EMAIL.matcher(email).matches()) {
            return showError(pending, model, "Revisá el email (por ejemplo nombre@gmail.com).");
        }
        // Si ya hay una cuenta con ese email, no se vincula sola: no sabemos si el email es de esta persona
        if (userRepository.findByEmail(email).isPresent()) {
            return showError(pending, model, "existing");
        }
        if (pending.getSends() >= PendingSocialSignup.MAX_SENDS) {
            return showError(pending, model, "Pediste demasiados códigos. Volvé a intentar entrar con Facebook más tarde.");
        }
        long wait = pending.secondsUntilResend();
        if (wait > 0) {
            return showError(pending, model, "Esperá " + wait + " segundos para pedir otro código.");
        }
        // Sin cuenta de correo configurada no podemos mandar el código
        if (!emailService.estaConfigurado()) {
            return showError(pending, model, "nomail");
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        boolean sent;
        try {
            // Se espera el resultado (hasta 20 s) para avisarle a la persona si el email no salió
            sent = emailService.enviar(email, "Tu código para crear la cuenta", "email/codigo",
                            Map.of("codigo", code, "nombre", firstName(pending.getName())),
                            EmailType.ACCOUNT_VERIFICATION, null, null)
                    .get(20, TimeUnit.SECONDS).ok();
        } catch (Exception e) {
            log.warn("No se pudo mandar el código de verificación a {}: {}", email, e.getMessage());
            sent = false;
        }
        if (!sent) {
            return showError(pending, model, "No pudimos mandarte el email. Revisá la dirección o probá en unos minutos.");
        }
        pending.codeSent(email, code);
        session.setAttribute(PendingSocialSignup.SESSION_KEY, pending); // se vuelve a guardar con los cambios
        return "redirect:/login/completar-email";
    }

    // Paso 2: revisa el código y crea la cuenta
    @PostMapping("/codigo")
    public String checkCode(@RequestParam("code") String rawCode, HttpSession session, Model model,
                            HttpServletRequest request, HttpServletResponse response) throws Exception {
        PendingSocialSignup pending = pending(session);
        if (pending == null) {
            return "redirect:/login?social=expired";
        }
        if (!pending.hasActiveCode()) {
            pending.forgetCode();
            session.setAttribute(PendingSocialSignup.SESSION_KEY, pending);
            return showError(pending, model, "El código venció o se usaron todos los intentos. Pedí uno nuevo.");
        }
        String code = rawCode == null ? "" : rawCode.replaceAll("\\s", "");
        // Comparación "de tiempo constante": no da pistas de cuántos números se acertaron
        boolean ok = MessageDigest.isEqual(code.getBytes(StandardCharsets.UTF_8),
                pending.getCode().getBytes(StandardCharsets.UTF_8));
        if (!ok) {
            pending.countAttempt();
            session.setAttribute(PendingSocialSignup.SESSION_KEY, pending);
            int left = PendingSocialSignup.MAX_CODE_ATTEMPTS - pending.getCodeAttempts();
            return showError(pending, model, left > 0
                    ? "El código no es correcto. Te quedan " + left + (left == 1 ? " intento." : " intentos.")
                    : "El código no es correcto. Pedí uno nuevo.");
        }

        // Código correcto: se crea la cuenta
        User user;
        try {
            user = socialLoginService.completePendingSignup(pending);
        } catch (IllegalStateException e) {
            session.removeAttribute(PendingSocialSignup.SESSION_KEY);
            return "redirect:/login?social=failed&via=" + via(pending);
        }
        session.removeAttribute(PendingSocialSignup.SESSION_KEY);
        logIn(user, pending, request, response);
        return null; // la respuesta (redirección) ya la escribió el successHandler
    }

    // "Usar otro email": vuelve al paso 1
    @PostMapping("/cambiar")
    public String changeEmail(HttpSession session) {
        PendingSocialSignup pending = pending(session);
        if (pending == null) {
            return "redirect:/login?social=expired";
        }
        pending.forgetCode();
        session.setAttribute(PendingSocialSignup.SESSION_KEY, pending);
        return "redirect:/login/completar-email";
    }

    // Inicia la sesión igual que si hubiera vuelto de Facebook con todo en orden
    private void logIn(User user, PendingSocialSignup pending, HttpServletRequest request,
                       HttpServletResponse response) throws Exception {
        SocialUserPrincipal.Plain principal = new SocialUserPrincipal.Plain(user.getEmail(), user.getRoles(),
                Map.of("id", pending.getProviderId(), "name", user.getName()), "id");
        OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(
                principal, principal.getAuthorities(), via(pending));

        // Nuevo número de sesión al iniciar sesión (evita que alguien "plante" una sesión de antemano)
        request.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context, request, response);

        // Mismo aviso que un login normal: así se anota el último ingreso (LoginTimestampListener)
        eventPublisher.publishEvent(new AuthenticationSuccessEvent(authentication));
        successHandler.onAuthenticationSuccess(request, response, authentication);
    }

    private PendingSocialSignup pending(HttpSession session) {
        Object value = session.getAttribute(PendingSocialSignup.SESSION_KEY);
        if (value instanceof PendingSocialSignup pending && !pending.isExpired()) {
            return pending;
        }
        session.removeAttribute(PendingSocialSignup.SESSION_KEY);
        return null;
    }

    private String showError(PendingSocialSignup pending, Model model, String error) {
        fillModel(pending, model);
        model.addAttribute("error", error);
        return "completar-email";
    }

    private void fillModel(PendingSocialSignup pending, Model model) {
        model.addAttribute("step", pending.hasActiveCode() ? "code" : "email");
        model.addAttribute("sentTo", pending.getEmail());
        model.addAttribute("firstName", firstName(pending.getName()));
        model.addAttribute("via", via(pending));
    }

    private static String via(PendingSocialSignup pending) {
        return pending.getProvider().name().toLowerCase();
    }

    private static String firstName(String name) {
        if (name == null || name.isBlank()) return "";
        return name.trim().split("\\s+")[0];
    }
}
