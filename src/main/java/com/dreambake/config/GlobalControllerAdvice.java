package com.dreambake.config;

import com.dreambake.entity.User;
import com.dreambake.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

// Asistente global que inyecta atuomáticamente la información del usuario logueado en todas
// las páginas html, evitando repetir lógica de búsqueda en cada controlador
// Se puede acceder al usuario actual mediante currentUser.name/currentUser.email

@ControllerAdvice // Observador global, el sistema siempre sabe quién es el usuario logueado.
public class GlobalControllerAdvice {

    @Autowired // Necesito usar "UserRepository", encárgate de crearlo y entrégamelo
    private UserRepository userRepository;

    @ModelAttribute("currentUser") // Toma este dato y hazlo accesible para todo el frontend
    public User getCurrentUser(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            // Buscamos al usuario por su email (username en Spring Security)
            return userRepository.findByEmail(authentication.getName()).orElse(null);
        }
        return null;
    }
}
