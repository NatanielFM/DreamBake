package com.dreambake.config;

import com.dreambake.controller.AuthController;
import com.dreambake.controller.ProfileController;
import com.dreambake.controller.UnsubscribeController;
import com.dreambake.controller.WebController;
import com.dreambake.service.BusinessSettingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

// Datos que necesitan todas las páginas públicas (header, footer y etiquetas para compartir).
// Se limita a los controladores de vistas públicas (assignableTypes) para no hacer
// consultas de más en cada llamada a la API.
@ControllerAdvice(assignableTypes = { WebController.class, AuthController.class, ProfileController.class,
        UnsubscribeController.class })
public class PublicViewAdvice {

    @Autowired
    private BusinessSettingService settingService;

    // Número de WhatsApp del negocio (se cambia desde Panel → Ajustes). Lo usan los links
    // "Escribinos por WhatsApp" y store.js para armar el mensaje del pedido.
    @ModelAttribute("whatsappNumber")
    public String whatsappNumber() {
        return settingService.getWhatsappNumber();
    }

    // Dirección del sitio (ej: https://dulcesuenos.com.ar). Las etiquetas Open Graph,
    // que arman la vista previa al compartir el link en WhatsApp o Instagram, piden URLs completas.
    @ModelAttribute("siteUrl")
    public String siteUrl() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }
}
