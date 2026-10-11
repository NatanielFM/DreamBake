package com.dreambake.controller;

import com.dreambake.audit.AuditText;
import com.dreambake.entity.AuditAction;
import com.dreambake.entity.AuditModule;
import com.dreambake.service.AuditService;
import com.dreambake.service.BusinessSettingService;
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
    private BusinessSettingService settingService;

    @Autowired
    private MailAccountService mailAccountService;

    // Registro de auditoría (paso 3.3): se usa directo donde hace falta el antes y el después
    @Autowired
    private AuditService audit;

    @GetMapping
    public Map<String, String> getSettings() {
        return Map.of("whatsapp", settingService.getWhatsappNumber());
    }

    public record SettingsRequest(String whatsapp) {
    }

    @PutMapping
    public ResponseEntity<?> updateSettings(@RequestBody SettingsRequest request) {
        String before = settingService.getWhatsappNumber();
        String error = settingService.saveWhatsappNumber(request.whatsapp());
        if (error != null) {
            return ResponseEntity.badRequest().body(Map.of("message", error));
        }
        String after = settingService.getWhatsappNumber();
        if (!before.equals(after)) {
            audit.log(AuditAction.CONFIG_CHANGE, AuditModule.SISTEMA, "Configuración", "whatsapp",
                    "Cambió el número de WhatsApp del negocio de " + before + " a " + after,
                    AuditText.change(Map.of("whatsapp", before), Map.of("whatsapp", after)));
        }
        return ResponseEntity.ok(Map.of("whatsapp", after));
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
