package com.dreambake.controller;

import com.dreambake.service.BusinessSettingService;
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
}
