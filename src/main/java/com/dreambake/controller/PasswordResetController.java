package com.dreambake.controller;

import com.dreambake.service.PasswordResetService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.regex.Pattern;

/**
 * Pantallas de "Olvidé mi contraseña" (paso 6.1). La lógica está en PasswordResetService.
 *
 *   GET  /recuperar        → formulario con el email
 *   POST /recuperar        → manda el link (siempre responde lo mismo, exista o no la cuenta)
 *   GET  /recuperar/nueva?token=…  → guarda el código en la sesión y vuelve a /recuperar/nueva SIN el código
 *   GET  /recuperar/nueva  → formulario de contraseña nueva (o aviso de link vencido)
 *   POST /recuperar/nueva  → cambia la contraseña y va al login con ?reset
 *
 * ¿Por qué el código pasa a la sesión? Para que no quede en la barra de direcciones ni en el historial
 * del navegador, y para que el formulario no lo tenga escrito en el HTML.
 */
@Controller
public class PasswordResetController {

    private static final String SESSION_TOKEN = "passwordResetToken";
    private static final Pattern EMAIL_RE = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final PasswordResetService resetService;

    public PasswordResetController(PasswordResetService resetService) {
        this.resetService = resetService;
    }

    @GetMapping("/recuperar")
    public String showRequestForm() {
        return "recuperar";
    }

    @PostMapping("/recuperar")
    public String requestLink(@RequestParam(value = "email", required = false) String email,
                              Model model, RedirectAttributes redirect) {
        String clean = email == null ? "" : email.trim();
        if (clean.isEmpty() || clean.length() > 254 || !EMAIL_RE.matcher(clean).matches()) {
            model.addAttribute("email", clean);
            model.addAttribute("emailError", clean.isEmpty() ? "Escribí tu email." : "Revisá el email (por ejemplo nombre@gmail.com).");
            return "recuperar";
        }
        switch (resetService.requestReset(clean)) {
            case NO_MAIL -> {
                model.addAttribute("email", clean);
                model.addAttribute("noMail", true);
                return "recuperar";
            }
            case TOO_MANY -> redirect.addFlashAttribute("tooMany", true);
            case OK -> redirect.addFlashAttribute("sent", true);
        }
        // Se redirige (patrón POST → redirect → GET) para que recargar la página no mande otro email
        return "redirect:/recuperar";
    }

    @GetMapping("/recuperar/nueva")
    public String showNewPasswordForm(@RequestParam(value = "token", required = false) String token,
                                      HttpSession session, Model model) {
        if (token != null) {
            session.setAttribute(SESSION_TOKEN, token);
            return "redirect:/recuperar/nueva";
        }
        model.addAttribute("validLink", resetService.isValid((String) session.getAttribute(SESSION_TOKEN)));
        model.addAttribute("minLength", PasswordResetService.MIN_LENGTH);
        return "recuperar-nueva";
    }

    @PostMapping("/recuperar/nueva")
    public String saveNewPassword(@RequestParam(value = "password", required = false) String password,
                                  @RequestParam(value = "confirmPassword", required = false) String confirmPassword,
                                  HttpSession session, Model model) {
        String token = (String) session.getAttribute(SESSION_TOKEN);
        String error = resetService.resetPassword(token, password, confirmPassword);
        if (error != null) {
            boolean linkOk = resetService.isValid(token);
            model.addAttribute("validLink", linkOk);
            model.addAttribute("minLength", PasswordResetService.MIN_LENGTH);
            if (linkOk) model.addAttribute("passwordError", error);
            return "recuperar-nueva";
        }
        session.removeAttribute(SESSION_TOKEN);
        return "redirect:/login?reset";
    }
}
