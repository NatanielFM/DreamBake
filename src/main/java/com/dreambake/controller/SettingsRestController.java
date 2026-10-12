package com.dreambake.controller;

import com.dreambake.audit.AuditText;
import com.dreambake.entity.AuditAction;
import com.dreambake.entity.AuditModule;
import com.dreambake.service.AuditService;
import com.dreambake.service.BusinessProfileService;
import com.dreambake.service.MailAccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// Ajustes del negocio (pestaña "Ajustes" del panel). Solo el administrador
// puede usar estos endpoints: lo define SecurityConfig ("/api/**" → ADMIN).
@RestController
@RequestMapping("/api/settings")
public class SettingsRestController {

    @Autowired
    private BusinessProfileService businessProfile;

    @Autowired
    private MailAccountService mailAccountService;

    // Registro de auditoría (paso 3.3): se usa directo donde hace falta el antes y el después
    @Autowired
    private AuditService audit;

    // Datos del negocio (paso 6.4): BusinessProfile. Todo lo valida y audita BusinessProfileService.
    @GetMapping
    public com.dreambake.entity.BusinessProfile getSettings() {
        return businessProfile.get();
    }

    @PutMapping
    public ResponseEntity<?> updateSettings(@RequestBody BusinessProfileService.Request request) {
        Map<String, String> errors = businessProfile.save(request);
        if (!errors.isEmpty()) {
            // El primer error va en "message" (como el resto de los endpoints) y todos en "errors" (uno por campo)
            return ResponseEntity.badRequest().body(Map.of("message", errors.values().iterator().next(), "errors", errors));
        }
        return ResponseEntity.ok(businessProfile.get());
    }

    // ===== Correo de envío (Ajustes → Correo de envío) =====
    // La contraseña NUNCA se devuelve: solo se dice si hay una guardada (hasPassword).

    @GetMapping("/mail")
    public MailAccountService.Info getMailAccount() {
        return mailAccountService.info();
    }

    // Prueba la cuenta con el servidor de correo y, si la acepta, la guarda (con la contraseña cifrada)
    @PutMapping("/mail")
    public ResponseEntity<?> saveMailAccount(@RequestBody MailAccountService.Request request) {
        String before = mailAccountService.info().username();
        String error = mailAccountService.save(request);
        if (error != null) {
            return ResponseEntity.badRequest().body(Map.of("message", error));
        }
        MailAccountService.Info after = mailAccountService.info();
        // La contraseña nunca se registra (ni cifrada): solo qué cuenta y qué servidor
        audit.log(AuditAction.CONFIG_CHANGE, AuditModule.SISTEMA, "Configuración", "correo",
                "Cambió la cuenta de correo de envío a " + after.username(),
                AuditText.change(Map.of("cuenta", String.valueOf(before)),
                        Map.of("cuenta", String.valueOf(after.username()), "servidor", after.host() + ":" + after.port())));
        return ResponseEntity.ok(after);
    }

    @DeleteMapping("/mail")
    public MailAccountService.Info removeMailAccount() {
        String before = mailAccountService.info().username();
        mailAccountService.remove();
        audit.log(AuditAction.CONFIG_CHANGE, AuditModule.SISTEMA, "Configuración", "correo",
                "Quitó la cuenta de correo de envío (" + before + ")", null);
        return mailAccountService.info();
    }
}
