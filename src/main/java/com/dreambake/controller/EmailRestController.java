package com.dreambake.controller;

import com.dreambake.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Herramientas de email del panel. Está bajo /api/**, que en SecurityConfig es solo para ADMIN.
 */
@RestController
@RequestMapping("/api/email")
public class EmailRestController {

    @Autowired
    private EmailService emailService;

    // Manda la plantilla base al email del administrador que tiene la sesión iniciada
    // (su email es su usuario de login). Sirve para comprobar que el correo está bien configurado.
    @PostMapping("/test")
    public ResponseEntity<?> sendTest(Authentication auth) {
        String destino = auth.getName();
        try {
            // El envío es asíncrono; acá se espera el resultado (hasta 25 s) para avisarle al administrador.
            // La pantalla no se traba: el navegador espera con el botón en "Enviando…".
            EmailService.Resultado r = emailService
                    .enviar(destino, "Email de prueba de DreamBake", "email/prueba", Map.of(), null)
                    .get(25, TimeUnit.SECONDS);
            if (r.ok()) {
                return ResponseEntity.ok(Map.of("message", "Te enviamos un email de prueba a " + destino
                        + ". Revisá tu bandeja de entrada (y la carpeta de spam)."));
            }
            return ResponseEntity.badRequest().body(Map.of("message", r.mensaje()));
        } catch (Exception e) {
            return ResponseEntity.status(504).body(Map.of("message",
                    "El servidor de correo tardó demasiado en responder. Probá de nuevo en unos minutos."));
        }
    }
}
