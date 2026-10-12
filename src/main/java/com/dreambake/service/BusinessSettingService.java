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

    // Datos del negocio editables (paso 6.4): el WhatsApp ahora vive ahí
    @Autowired
    private BusinessProfileService businessProfile;

    // Número de WhatsApp del negocio, solo dígitos y con código de país (ej: 5493764159312).
    // Desde el paso 6.4 sale de BusinessProfile (Ajustes → Configuración del negocio).
    public String getWhatsappNumber() {
        return businessProfile.get().getWhatsapp();
    }

    // ===== Fidelización: saludo de cumpleaños (se maneja desde Clientes → Fidelización) =====
    public static final String BIRTHDAY_ENABLED = "loyalty.birthday.enabled";
    public static final String BIRTHDAY_PERCENTAGE = "loyalty.birthday.percentage";
    public static final int DEFAULT_BIRTHDAY_PERCENTAGE = 15;

    // ¿Están activados los saludos automáticos? Por defecto sí (solo les llegan a quienes aceptaron promociones).
    public boolean isBirthdayEnabled() {
        return settingRepository.findById(BIRTHDAY_ENABLED)
                .map(BusinessSetting::getValue)
                .map(v -> !"false".equalsIgnoreCase(v))
                .orElse(true);
    }

    public void setBirthdayEnabled(boolean enabled) {
        settingRepository.save(new BusinessSetting(BIRTHDAY_ENABLED, String.valueOf(enabled)));
    }

    // Porcentaje de descuento del cupón de cumpleaños (por defecto 15)
    public int getBirthdayPercentage() {
        return settingRepository.findById(BIRTHDAY_PERCENTAGE)
                .map(BusinessSetting::getValue)
                .map(v -> { try { return Integer.parseInt(v.trim()); } catch (Exception e) { return null; } })
                .filter(p -> p >= 1 && p <= 100)
                .orElse(DEFAULT_BIRTHDAY_PERCENTAGE);
    }

    // Devuelve null si se guardó, o el mensaje de error para mostrar
    public String saveBirthdayPercentage(Integer percentage) {
        if (percentage == null || percentage < 1 || percentage > 100) {
            return "El descuento tiene que ser un número entero entre 1 y 100.";
        }
        settingRepository.save(new BusinessSetting(BIRTHDAY_PERCENTAGE, String.valueOf(percentage)));
        return null;
    }

    // ===== Campañas: tope diario de emails (límite de Gmail) =====
    public static final String CAMPAIGN_DAILY_LIMIT = "campaign.daily-limit";
    public static final int DEFAULT_CAMPAIGN_DAILY_LIMIT = 400;

    // Cuántos emails se pueden mandar por día en total (Gmail gratis permite unos 500)
    public int getCampaignDailyLimit() {
        return settingRepository.findById(CAMPAIGN_DAILY_LIMIT)
                .map(BusinessSetting::getValue)
                .map(v -> { try { return Integer.parseInt(v.trim()); } catch (Exception e) { return null; } })
                .filter(n -> n >= 1 && n <= 2000)
                .orElse(DEFAULT_CAMPAIGN_DAILY_LIMIT);
    }

    // Devuelve null si se guardó, o el mensaje de error para mostrar
    public String saveCampaignDailyLimit(Integer limit) {
        if (limit == null || limit < 1 || limit > 2000) {
            return "El máximo de emails por día tiene que ser un número entre 1 y 2000.";
        }
        settingRepository.save(new BusinessSetting(CAMPAIGN_DAILY_LIMIT, String.valueOf(limit)));
        return null;
    }
}
