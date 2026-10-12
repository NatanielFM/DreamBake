package com.dreambake.config;

import com.dreambake.controller.AuthController;
import com.dreambake.controller.PasswordResetController;
import com.dreambake.controller.ProfileController;
import com.dreambake.controller.SocialSignupController;
import com.dreambake.controller.UnsubscribeController;
import com.dreambake.controller.WebController;
import com.dreambake.service.BusinessSettingService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

// Datos que necesitan todas las páginas públicas (header, footer y etiquetas para compartir).
// Se limita a los controladores de vistas públicas (assignableTypes) para no hacer
// consultas de más en cada llamada a la API.
@ControllerAdvice(assignableTypes = { WebController.class, AuthController.class, ProfileController.class,
        UnsubscribeController.class, SocialSignupController.class, PasswordResetController.class })
public class PublicViewAdvice {

    @Autowired
    private AppProperties app;

    @Autowired
    private com.dreambake.service.BusinessProfileService businessProfile;

    @Autowired
    private BusinessSettingService settingService;

    // Lista de redes sociales configuradas. Spring Boot solo la crea si hay credenciales cargadas.
    @Autowired
    private ObjectProvider<ClientRegistrationRepository> socialClients;

    // ¿Se muestra el botón "Continuar con Google"? Solo si hay client-id de Google cargado.
    @ModelAttribute("googleLoginEnabled")
    public boolean googleLoginEnabled() {
        return isConfigured("google");
    }

    // ¿Se muestra el botón "Continuar con Facebook"? Solo si hay App ID de Facebook cargado.
    @ModelAttribute("facebookLoginEnabled")
    public boolean facebookLoginEnabled() {
        return isConfigured("facebook");
    }

    private boolean isConfigured(String registrationId) {
        ClientRegistrationRepository repo = socialClients.getIfAvailable();
        return repo != null && repo.findByRegistrationId(registrationId) != null;
    }

    // Número de WhatsApp del negocio (se cambia desde Panel → Ajustes). Lo usan los links
    // "Escribinos por WhatsApp" y store.js para armar el mensaje del pedido.
    // Datos del negocio (paso 6.4, Ajustes → Configuración del negocio): nombre, WhatsApp, email,
    // dirección, Instagram, Facebook, horario, anticipación, seña, mensaje de la tienda y pausa.
    // Las plantillas los usan como ${negocio.name}, ${negocio.instagramUrl}, ${negocio.minLeadDays}…
    @ModelAttribute("negocio")
    public com.dreambake.entity.BusinessProfile negocio() {
        return businessProfile.get();
    }

    @ModelAttribute("whatsappNumber")
    public String whatsappNumber() {
        return settingService.getWhatsappNumber();
    }

    // Dirección del sitio (ej: https://dulcesuenos.com.ar). Las etiquetas Open Graph,
    // que arman la vista previa al compartir el link en WhatsApp o Instagram, piden URLs completas.
    // Sale de app.public-url (paso 6.3), igual que el sitemap: detrás de Tailscale Funnel o de un proxy,
    // la dirección que ve el servidor (http://127.0.0.1:8080) no es la que conoce el público.
    @ModelAttribute("siteUrl")
    public String siteUrl() {
        return app.getPublicUrl();
    }
}
