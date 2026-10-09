package com.dreambake.controller;

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

    @GetMapping
    public Map<String, String> getSettings() {
        return Map.of("whatsapp", settingService.getWhatsappNumber());
    }

    public record SettingsRequest(String whatsapp) {
    }

    @PutMapping
    public ResponseEntity<?> updateSettings(@RequestBody SettingsRequest request) {
        String error = settingService.saveWhatsappNumber(request.whatsapp());
        if (error != null) {
            return ResponseEntity.badRequest().body(Map.of("message", error));
        }
        return ResponseEntity.ok(Map.of("whatsapp", settingService.getWhatsappNumber()));
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
        String error = mailAccountService.save(request);
        if (error != null) {
            return ResponseEntity.badRequest().body(Map.of("message", error));
        }
        return ResponseEntity.ok(mailAccountService.info());
    }

    @DeleteMapping("/mail")
    public MailAccountService.Info removeMailAccount() {
        mailAccountService.remove();
        return mailAccountService.info();
    }
}
