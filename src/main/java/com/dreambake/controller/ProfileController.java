package com.dreambake.controller;

import com.dreambake.entity.AuthProvider;
import com.dreambake.entity.User;
import com.dreambake.repository.UserRepository;
import com.dreambake.service.SocialLoginService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;

@Controller
@RequestMapping("/profile")
public class ProfileController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // Página "Mi cuenta". Además de los datos (currentUser, de GlobalControllerAdvice) se le dice
    // si la cuenta tiene Google vinculado y si el empleado puede vincular/desvincular ahora.
    @GetMapping
    public String profilePage(Authentication authentication, Model model) {
        User user = authentication == null ? null
                : userRepository.findByEmail(authentication.getName()).orElse(null);
        boolean staff = user != null && SocialLoginService.isStaff(user);
        model.addAttribute("googleLinked", user != null && user.getAuthProvider() == AuthProvider.GOOGLE);
        model.addAttribute("isStaff", staff);
        model.addAttribute("passwordSession", enteredWithPassword(authentication));
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

    @PostMapping("/update")
    public String updateProfile(
            @RequestParam("name") String name,
            @RequestParam(value = "phone", required = false) String phone,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }

        User user = userRepository.findByEmail(authentication.getName()).orElse(null);
        if (user != null) {
            user.setName(name.trim());
            user.setPhone(phone != null ? phone.trim() : "");
            userRepository.save(user);
        }

        return "redirect:/profile?updated=true";
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

        return "redirect:/profile?passUpdated=true";
    }
}