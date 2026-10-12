package com.dreambake.controller;

import com.dreambake.entity.User;
import com.dreambake.repository.UserRepository;
import com.dreambake.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Campanita de avisos de los paneles (paso 6.6). ADMIN y PASTELERO (SecurityConfig); cada uno recibe
 * solo los avisos de su rol y sus propios recordatorios.
 *
 * Marcar como leído NO se audita: no cambia datos del negocio, solo qué vio cada persona en su campanita
 * (igual que charlar con Bakey). Auditarlo llenaría el registro de entradas sin valor.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationRestController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public NotificationRestController(NotificationService notificationService, UserRepository userRepository) {
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    // Avisos actuales: {unread, items:[{key, type, title, text, tab, read, sound}]}
    @GetMapping
    public NotificationService.Result list(Authentication auth) {
        return notificationService.forUser(currentUser(auth), isAdmin(auth));
    }

    public record ReadRequest(List<String> keys) {}

    // Marcar como leídos (uno o "todas": el panel manda las claves que está mostrando)
    @PostMapping("/read")
    public ResponseEntity<?> markRead(@RequestBody ReadRequest request, Authentication auth) {
        int marked = notificationService.markRead(currentUser(auth), request == null ? List.of() : request.keys());
        return ResponseEntity.ok(Map.of("marked", marked));
    }

    private User currentUser(Authentication auth) {
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new IllegalStateException("Usuario de la sesión no encontrado"));
    }

    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ADMIN"));
    }
}
