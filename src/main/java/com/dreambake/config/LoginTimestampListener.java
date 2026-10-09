package com.dreambake.config;

import com.dreambake.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Anota la fecha y hora del último ingreso de cada usuario.
 * Spring Security avisa con un AuthenticationSuccessEvent cada vez que un login sale bien
 * (usuario y contraseña ahora, y más adelante también Google y Facebook).
 */
@Component
public class LoginTimestampListener {

    @Autowired
    private UserRepository userRepository;

    @EventListener
    public void onLoginSuccess(AuthenticationSuccessEvent event) {
        if (event.getAuthentication() instanceof AnonymousAuthenticationToken) return;
        String email = event.getAuthentication().getName();
        if (email == null || email.isBlank()) return;
        try {
            userRepository.updateLastLogin(email, LocalDateTime.now());
        } catch (Exception e) {
            // Anotar la fecha es un extra: si falla, el usuario igual tiene que poder entrar
        }
    }
}
