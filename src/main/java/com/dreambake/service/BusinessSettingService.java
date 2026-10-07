package com.dreambake.service;

import com.dreambake.entity.BusinessSetting;
import com.dreambake.repository.BusinessSettingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// Lee y guarda los ajustes del negocio (tabla business_settings).
// Si un ajuste todavía no se guardó nunca, se usa su valor por defecto.
@Service
public class BusinessSettingService {

    public static final String WHATSAPP = "whatsapp";

    // Número que estaba fijo en el código antes de que existiera este ajuste
    public static final String DEFAULT_WHATSAPP = "5493764159312";

    @Autowired
    private BusinessSettingRepository settingRepository;

    // Número de WhatsApp del negocio, solo dígitos y con código de país (ej: 5493764159312)
    public String getWhatsappNumber() {
        return settingRepository.findById(WHATSAPP)
                .map(BusinessSetting::getValue)
                .filter(v -> v != null && !v.isBlank())
                .orElse(DEFAULT_WHATSAPP);
    }

    // Devuelve null si el número es válido y se guardó, o el mensaje de error para mostrar
    public String saveWhatsappNumber(String raw) {
        // Se quitan espacios, guiones, paréntesis y el "+" que la gente suele escribir
        String digits = raw == null ? "" : raw.replaceAll("[\\s\\-()+]", "");
        if (!digits.matches("\\d{10,15}")) {
            return "Escribí el número completo, solo con números: código de país, área y número (ej: 5493764159312).";
        }
        settingRepository.save(new BusinessSetting(WHATSAPP, digits));
        return null;
    }
}
