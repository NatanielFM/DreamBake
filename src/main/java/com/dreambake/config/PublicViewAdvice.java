package com.dreambake.config;

import com.dreambake.controller.AuthController;
import com.dreambake.controller.ProfileController;
import com.dreambake.controller.WebController;
import com.dreambake.service.BusinessSettingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

// Datos que necesitan todas las páginas públicas (header, footer y etiquetas para compartir).
// Se limita a los controladores de vistas públicas (assignableTypes) para no hacer
// consultas de más en cada llamada a la API.
@ControllerAdvice(assignableTypes = { WebController.class, AuthController.class, ProfileController.class })
public class PublicViewAdvice {

    @Autowired
    private BusinessSettingService settingService;

    // Número de WhatsApp del negocio (se cambia desde Panel → Ajustes). Lo usan los links
    // "Escribinos por WhatsApp" y store.js para armar el mensaje del pedido.
    @ModelAttribute("whatsappNumber")
    public String whatsappNumber() {
        return settingService.getWhatsappNumber();
    }

    // El mismo número, pero escrito para que lo lea una persona (lo muestra el pie de página).
    // Ej: 5493764159312 → "+54 9 376 415-9312"
    @ModelAttribute("whatsappDisplay")
    public String whatsappDisplay() {
        return formatWhatsappForDisplay(settingService.getWhatsappNumber());
    }

    // Arma el formato de un celular argentino: 54 (país) + 9 (celular) + 10 dígitos (área + número).
    // Si el número es de otro país o tiene otro largo, se muestra tal cual con un "+" adelante,
    // así nunca se muestra un número mal cortado.
    static String formatWhatsappForDisplay(String number) {
        if (number == null || !number.matches("549\\d{10}")) {
            return "+" + number;
        }
        String rest = number.substring(3); // los 10 dígitos de área + número
        // Buenos Aires (área 11): 11 XXXX-XXXX
        if (rest.startsWith("11")) {
            return "+54 9 11 " + rest.substring(2, 6) + "-" + rest.substring(6);
        }
        // Resto del país (ej: Posadas, área 376): 376 XXX-XXXX
        return "+54 9 " + rest.substring(0, 3) + " " + rest.substring(3, 6) + "-" + rest.substring(6);
    }

    // Dirección del sitio (ej: https://dulcesuenos.com.ar). Las etiquetas Open Graph,
    // que arman la vista previa al compartir el link en WhatsApp o Instagram, piden URLs completas.
    @ModelAttribute("siteUrl")
    public String siteUrl() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }
}
