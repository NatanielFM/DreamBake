package com.dreambake.controller;

import com.dreambake.entity.AuditAction;
import com.dreambake.entity.AuditModule;
import com.dreambake.service.AuditService;
import com.dreambake.entity.AuthProvider;
import com.dreambake.entity.Customer;
import com.dreambake.entity.Order;
import com.dreambake.entity.User;
import com.dreambake.repository.UserRepository;
import com.dreambake.service.CustomerAccountService;
import com.dreambake.service.EmailCodeChallenge;
import com.dreambake.service.EmailCodeService;
import com.dreambake.service.SocialLoginService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

@Controller
@RequestMapping("/profile")
public class ProfileController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // Registro de auditoría (paso 3.3)
    @Autowired
    private AuditService audit;

    @Autowired
    private CustomerAccountService accountService;

    @Autowired
    private EmailCodeService emailCodeService;

    // Código de "Confirmá tu email" que se está esperando (se guarda en la sesión)
    private static final String SESSION_EMAIL_CHALLENGE = "profile.emailChallenge";

    // Página "Mi cuenta". Además de los datos (currentUser, de GlobalControllerAdvice) se le dice
    // si la cuenta tiene Google vinculado y si el empleado puede vincular/desvincular ahora.
    @GetMapping
    public String profilePage(Authentication authentication, Model model, HttpSession session) {
        User user = authentication == null ? null
                : userRepository.findByEmail(authentication.getName()).orElse(null);
        boolean staff = user != null && SocialLoginService.isStaff(user);
        model.addAttribute("googleLinked", user != null && user.getAuthProvider() == AuthProvider.GOOGLE);
        model.addAttribute("facebookLinked", user != null && user.getAuthProvider() == AuthProvider.FACEBOOK);
        model.addAttribute("isStaff", staff);
        model.addAttribute("passwordSession", enteredWithPassword(authentication));

        // Área del cliente (paso 2.4)
        boolean client = user != null && !staff;
        boolean customerArea = accountService.canUseCustomerArea(user);
        model.addAttribute("isClient", client);
        model.addAttribute("customerArea", customerArea);
        if (customerArea) {
            Customer customer = accountService.findCustomer(user).orElse(null);
            model.addAttribute("birthDate", customer != null ? customer.getBirthDate() : null);
            model.addAttribute("marketingConsent", customer != null && Boolean.TRUE.equals(customer.getMarketingConsent()));
        }
        if (client) {
            // "Confirmá tu email": ¿ya le mandamos un código que todavía sirve?
            EmailCodeChallenge challenge = (EmailCodeChallenge) session.getAttribute(SESSION_EMAIL_CHALLENGE);
            model.addAttribute("verifyCodeSent", !customerArea && challenge != null && challenge.hasActiveCode());
            // Pedidos en curso: mientras haya alguno no se puede eliminar la cuenta
            List<Order> active = accountService.activeOrders(user);
            model.addAttribute("activeOrders", active);
        }
        return "profile";
    }

    // "Vincular cuenta de Google" (solo personal que entró con contraseña). Se anota en la sesión
    // quién lo pidió y se manda a Google; al volver, SocialLoginService une esa cuenta de Google
    // a este usuario (aunque el email de Google sea otro).
    @PostMapping("/google/link")
    public String linkGoogle(Authentication authentication, HttpSession session) {
        User user = staffWithPassword(authentication);
        if (user == null) {
            return "redirect:/profile?linkError=password";
        }
        session.setAttribute(SocialLoginService.SESSION_LINK_USER_ID, user.getId());
        session.setAttribute(SocialLoginService.SESSION_LINK_STARTED_AT, Instant.now());
        session.setAttribute(SocialLoginService.SESSION_LINK_PROVIDER, AuthProvider.GOOGLE);
        session.setAttribute(SocialLoginService.SESSION_LINK_PREVIOUS_AUTH, authentication);
        return "redirect:/oauth2/authorization/google";
    }

    // "Desvincular": la cuenta vuelve a entrar solo con contraseña
    @PostMapping("/google/unlink")
    public String unlinkGoogle(Authentication authentication) {
        User user = staffWithPassword(authentication);
        if (user == null) {
            return "redirect:/profile?linkError=password";
        }
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setProviderId(null);
        user.setAvatarUrl(null);
        userRepository.save(user);
        audit.log(AuditAction.UPDATE, AuditModule.SEGURIDAD, "Usuario", user.getId(),
                "Desvinculó su cuenta de Google (vuelve a entrar solo con contraseña)", null);
        return "redirect:/profile?unlinked=google";
    }

    // Solo cuenta como "entró con contraseña" el login con el formulario en esta sesión.
    // No vale "Mantener la sesión iniciada" (la cookie podría estar en una compu ajena) ni Google.
    private boolean enteredWithPassword(Authentication authentication) {
        return authentication instanceof UsernamePasswordAuthenticationToken && authentication.isAuthenticated();
    }

    private User staffWithPassword(Authentication authentication) {
        if (!enteredWithPassword(authentication)) return null;
        User user = userRepository.findByEmail(authentication.getName()).orElse(null);
        return (user != null && SocialLoginService.isStaff(user)) ? user : null;
    }

    // "Datos personales". Nombre y teléfono para todos; los clientes con el email comprobado además
    // guardan cumpleaños y promociones en su ficha de cliente (CustomerAccountService.saveProfile).
    @PostMapping("/update")
    public String updateProfile(
            @RequestParam("name") String name,
            @RequestParam(value = "phone", required = false) String phone,
            @RequestParam(value = "birthDate", required = false) String birthDate,
            @RequestParam(value = "marketingConsent", required = false) String marketingConsent,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }

        User user = userRepository.findByEmail(authentication.getName()).orElse(null);
        if (user != null) {
            String cleanName = name == null ? "" : name.trim();
            if (cleanName.length() < 3 || cleanName.length() > 120) {
                return "redirect:/profile?error=name";
            }
            String cleanPhone = phone != null ? phone.trim() : "";
            if (cleanPhone.length() > 40) cleanPhone = cleanPhone.substring(0, 40);
            accountService.saveProfile(user, cleanName, cleanPhone, parseDate(birthDate), marketingConsent != null);
        }

        return "redirect:/profile?updated=true";
    }

    // "Confirmá tu email" — paso 1: le mandamos un código al email de su cuenta (nunca a otro)
    @PostMapping("/email/code")
    public String sendEmailCode(Authentication authentication, HttpSession session, RedirectAttributes flash) {
        User user = unverifiedClient(authentication);
        if (user == null) return "redirect:/profile";
        EmailCodeChallenge challenge = (EmailCodeChallenge) session.getAttribute(SESSION_EMAIL_CHALLENGE);
        if (challenge == null) challenge = new EmailCodeChallenge();
        String error = emailCodeService.send(challenge, user.getEmail(), firstName(user.getName()), "confirmación de email");
        session.setAttribute(SESSION_EMAIL_CHALLENGE, challenge);
        if (error != null) {
            flash.addFlashAttribute("verifyError", EmailCodeService.NO_MAIL.equals(error)
                    ? "Ahora no podemos mandar emails. Probá de nuevo más tarde." : error);
        }
        return "redirect:/profile#email-verify";
    }

    // "Confirmá tu email" — paso 2: revisa el código
    @PostMapping("/email/verify")
    public String verifyEmail(@RequestParam("code") String code, Authentication authentication,
                              HttpSession session, RedirectAttributes flash) {
        User user = unverifiedClient(authentication);
        if (user == null) return "redirect:/profile";
        EmailCodeChallenge challenge = (EmailCodeChallenge) session.getAttribute(SESSION_EMAIL_CHALLENGE);
        if (challenge == null || !challenge.hasActiveCode()) {
            flash.addFlashAttribute("verifyError", "El código venció o se usaron todos los intentos. Pedí uno nuevo.");
            return "redirect:/profile#email-verify";
        }
        // El código tiene que haberse mandado al email de ESTA cuenta
        boolean ok = user.getEmail().equalsIgnoreCase(challenge.getEmail()) && challenge.checkCode(code);
        session.setAttribute(SESSION_EMAIL_CHALLENGE, challenge);
        if (!ok) {
            int left = challenge.attemptsLeft();
            flash.addFlashAttribute("verifyError", left > 0
                    ? "El código no es correcto. Te quedan " + left + (left == 1 ? " intento." : " intentos.")
                    : "El código no es correcto. Pedí uno nuevo.");
            return "redirect:/profile#email-verify";
        }
        session.removeAttribute(SESSION_EMAIL_CHALLENGE);
        user.setEmailVerified(true);
        userRepository.save(user);
        audit.log(AuditAction.UPDATE, AuditModule.SEGURIDAD, "Usuario", user.getId(),
                "Confirmó su email con el código", null);
        return "redirect:/profile?verified=true";
    }

    // "Eliminar mi cuenta" (solo clientes). Borra el usuario y saca los datos personales de su ficha
    // y de sus pedidos (CustomerAccountService.deleteAccount). Después cierra la sesión.
    @PostMapping("/delete")
    public String deleteAccount(@RequestParam(value = "confirm", required = false) String confirm,
                                Authentication authentication, HttpServletRequest request,
                                HttpServletResponse response) {
        if (authentication == null) return "redirect:/login";
        User user = userRepository.findByEmail(authentication.getName()).orElse(null);
        if (user == null || SocialLoginService.isStaff(user)) {
            return "redirect:/profile";
        }
        // La ventana de confirmación manda confirm=si: evita borrar la cuenta con un envío accidental
        if (!"si".equals(confirm)) {
            return "redirect:/profile";
        }
        if (!accountService.deleteAccount(user)) {
            return "redirect:/profile?deleteError=active#delete-account";
        }
        // Cierra la sesión y borra la cookie de "Mantener la sesión iniciada"
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        Cookie rememberMe = new Cookie("remember-me", null);
        rememberMe.setMaxAge(0);
        rememberMe.setPath("/");
        response.addCookie(rememberMe);
        return "redirect:/login?deleted";
    }

    // Cliente logueado con el email todavía sin comprobar (los únicos que usan "Confirmá tu email")
    private User unverifiedClient(Authentication authentication) {
        if (authentication == null) return null;
        User user = userRepository.findByEmail(authentication.getName()).orElse(null);
        if (user == null || SocialLoginService.isStaff(user) || Boolean.TRUE.equals(user.getEmailVerified())) return null;
        return user;
    }

    // Fecha del campo <input type="date"> (aaaa-mm-dd). Vacía o mal escrita = sin fecha.
    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String firstName(String name) {
        if (name == null || name.isBlank()) return "";
        return name.trim().split("\\s+")[0];
    }

    @PostMapping("/password")
    public String updatePassword(
            @RequestParam("currentPassword") String currentPassword,
            @RequestParam("newPassword") String newPassword,
            @RequestParam("confirmPassword") String confirmPassword,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }

        User user = userRepository.findByEmail(authentication.getName()).orElse(null);
        if (user == null) {
            return "redirect:/login";
        }

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            return "redirect:/profile?error=current";
        }

        if (newPassword == null || newPassword.length() < 6 || !newPassword.equals(confirmPassword)) {
            return "redirect:/profile?error=match";
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        audit.log(AuditAction.UPDATE, AuditModule.SEGURIDAD, "Usuario", user.getId(), "Cambió su contraseña", null);

        return "redirect:/profile?passUpdated=true";
    }
}