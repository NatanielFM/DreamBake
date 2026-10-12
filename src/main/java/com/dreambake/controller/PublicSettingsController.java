package com.dreambake.controller;

import com.dreambake.service.BusinessProfileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Datos públicos del negocio para el JavaScript de la tienda (paso 6.4): WhatsApp, redes, anticipación
 * mínima, si los pedidos están pausados… Es público (sin iniciar sesión) y solo devuelve lo que ya se
 * ve en la página; los datos internos y la auditoría nunca salen por acá.
 */
@RestController
public class PublicSettingsController {

    private final BusinessProfileService businessProfile;

    public PublicSettingsController(BusinessProfileService businessProfile) {
        this.businessProfile = businessProfile;
    }

    @GetMapping("/api/public/settings")
    public Map<String, Object> publicSettings() {
        return businessProfile.publicView();
    }
}
