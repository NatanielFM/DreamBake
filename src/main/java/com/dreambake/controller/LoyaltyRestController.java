package com.dreambake.controller;

import com.dreambake.audit.Auditable;
import com.dreambake.audit.AuditText;
import com.dreambake.entity.AuditAction;
import com.dreambake.entity.AuditModule;
import com.dreambake.service.AuditService;
import java.util.LinkedHashMap;
import com.dreambake.repository.CustomerRepository;
import com.dreambake.service.BirthdayService;
import com.dreambake.service.BusinessSettingService;
import com.dreambake.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Fidelización (Clientes → Fidelización): configuración de los saludos de cumpleaños,
 * próximos cumpleaños y envío manual. Está bajo /api/**, o sea solo para ADMIN (SecurityConfig).
 */
@RestController
@RequestMapping("/api/loyalty")
public class LoyaltyRestController {

    @Autowired private BusinessSettingService settingService;
    @Autowired private BirthdayService birthdayService;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private EmailService emailService;
    @Autowired private AuditService audit; // registro de auditoría (paso 3.3)

    // ----- Configuración (se guarda en la base: tabla business_settings) -----

    // dailyLimit: máximo de emails por día (lo usan las campañas; cuenta todos los emails del día)
    public record LoyaltySettings(boolean enabled, int percentage, boolean emailConfigured, int dailyLimit) {}

    @GetMapping("/settings")
    public LoyaltySettings getSettings() {
        return new LoyaltySettings(settingService.isBirthdayEnabled(), settingService.getBirthdayPercentage(),
                emailService.estaConfigurado(), settingService.getCampaignDailyLimit());
    }

    public record SettingsRequest(Boolean enabled, Integer percentage, Integer dailyLimit) {}

    @PutMapping("/settings")
    public ResponseEntity<?> saveSettings(@RequestBody SettingsRequest req) {
        LoyaltySettings before = getSettings(); // para la auditoría (antes y después)
        if (req.percentage() != null) {
            String error = settingService.saveBirthdayPercentage(req.percentage());
            if (error != null) return ResponseEntity.badRequest().body(Map.of("message", error));
        }
        if (req.dailyLimit() != null) {
            String error = settingService.saveCampaignDailyLimit(req.dailyLimit());
            if (error != null) return ResponseEntity.badRequest().body(Map.of("message", error));
        }
        if (req.enabled() != null) settingService.setBirthdayEnabled(req.enabled());
        LoyaltySettings after = getSettings();
        Map<String, Object> diff = AuditText.diff(loyaltySnapshot(before), loyaltySnapshot(after));
        audit.log(AuditAction.CONFIG_CHANGE, AuditModule.FIDELIZACION, "Configuración", null,
                "Cambió la configuración de fidelización" + AuditText.changedFields(diff), diff);
        return ResponseEntity.ok(after);
    }

    private static Map<String, Object> loyaltySnapshot(LoyaltySettings s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("saludosAutomaticos", s.enabled());
        m.put("descuentoCumpleanos", s.percentage());
        m.put("maximoEmailsPorDia", s.dailyLimit());
        return m;
    }

    // ----- Próximos cumpleaños -----

    // Cumpleaños de hoy y de los próximos 30 días
    @GetMapping("/birthdays")
    public List<BirthdayService.UpcomingBirthday> upcoming() {
        return birthdayService.upcoming(30);
    }

    // Revisa los cumpleaños de HOY ahora mismo (lo mismo que hace la tarea de las 9:00),
    // sin esperar la hora. No repite a quien ya fue saludado este año.
    @PostMapping("/birthdays/run")
    @Auditable(action = AuditAction.EMAIL_CAMPAIGN, module = AuditModule.FIDELIZACION, entityType = "Cumpleaños",
            description = "Mandó en el momento los saludos de cumpleaños de hoy (#{#result.sent()} enviados)")
    public BirthdayService.RunSummary runToday() {
        return birthdayService.runToday("botón del panel");
    }

    // "Enviar saludo ahora": manda el email de cumpleaños con su cupón a un cliente.
    // Si ya se le mandó este año responde 409 (el panel pregunta si se quiere mandar igual: force=true).
    @PostMapping("/birthdays/{customerId}/send")
    public ResponseEntity<?> sendNow(@PathVariable Long customerId, @RequestParam(defaultValue = "false") boolean force) {
        return customerRepository.findById(customerId).<ResponseEntity<?>>map(c -> {
            BirthdayService.GreetResult r = birthdayService.greet(c, force);
            Map<String, Object> body = Map.of("message", r.message(), "code", r.code() == null ? "" : r.code());
            if (r.outcome() == BirthdayService.Outcome.SENT) {
                audit.log(AuditAction.EMAIL_CAMPAIGN, AuditModule.FIDELIZACION, "Cliente", customerId,
                        "Mandó el saludo de cumpleaños a «" + c.getName() + "» (cupón " + r.code() + ")", null);
            }
            return switch (r.outcome()) {
                case SENT -> ResponseEntity.ok(body);
                case ALREADY_SENT -> ResponseEntity.status(HttpStatus.CONFLICT).body(body);
                default -> ResponseEntity.badRequest().body(body);
            };
        }).orElse(ResponseEntity.notFound().build());
    }

    // "Saludar por WhatsApp": devuelve el link wa.me con el mensaje y el cupón ya escritos
    @PostMapping("/birthdays/{customerId}/whatsapp")
    @Auditable(action = AuditAction.CREATE, module = AuditModule.FIDELIZACION, entityType = "Cliente", entityId = "#customerId",
            description = "Preparó el saludo de cumpleaños por WhatsApp del cliente ##{#customerId} (cupón #{#body.get('code')})")
    public ResponseEntity<?> whatsapp(@PathVariable Long customerId) {
        return customerRepository.findById(customerId).<ResponseEntity<?>>map(c ->
                birthdayService.whatsappGreeting(c)
                        .<ResponseEntity<?>>map(w -> ResponseEntity.ok(
                                Map.of("url", w.url(), "message", w.message(), "code", w.code())))
                        .orElse(ResponseEntity.badRequest().body(Map.of("message",
                                "El teléfono de este cliente no sirve para WhatsApp. Revisalo en su ficha."))))
                .orElse(ResponseEntity.notFound().build());
    }
}
