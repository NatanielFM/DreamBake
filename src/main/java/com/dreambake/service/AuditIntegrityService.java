package com.dreambake.service;

import com.dreambake.entity.BusinessSetting;
import com.dreambake.entity.EmailType;
import com.dreambake.entity.User;
import com.dreambake.repository.BusinessSettingRepository;
import com.dreambake.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Control de integridad del registro de auditoría (paso 3.2).
 *
 * check() ejecuta AuditService.verifyChain(). Si la cadena está rota:
 *  - deja un aviso guardado (business_settings audit.alert.*) que el panel muestra arriba de todo
 *    hasta que el administrador toque "Entendido";
 *  - manda un email a cada administrador.
 * Si la alteración es la MISMA que ya se avisó (mismo registro), no se vuelve a mandar el email ni
 * a mostrar el aviso cada semana: la cadena rota no se "arregla" sola, y repetirlo sería ruido.
 * Si aparece una alteración en otro registro, se avisa de nuevo.
 */
@Service
public class AuditIntegrityService {

    private static final Logger log = LoggerFactory.getLogger(AuditIntegrityService.class);
    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // Claves del aviso en business_settings
    static final String ALERT_ID = "audit.alert.broken-id";
    static final String ALERT_MESSAGE = "audit.alert.message";
    static final String ALERT_DETECTED = "audit.alert.detected-at";
    static final String ALERT_ACK = "audit.alert.acknowledged";
    static final String LAST_CHECK = "audit.last-check";

    /** Aviso para mostrar en el panel. */
    public record Alert(Long brokenId, String message, String detectedAt) {}

    @Autowired
    private AuditService auditService;

    @Autowired
    private BusinessSettingRepository settings;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailService emailService;

    /** Verifica la cadena y, si está rota, avisa. reason dice quién la pidió (para el log). */
    public AuditService.ChainCheck check(String reason) {
        AuditService.ChainCheck result = auditService.verifyChain();
        String now = LocalDateTime.now(ZONE).format(WHEN);
        put(LAST_CHECK, now + " — " + (result.intact() ? "íntegra" : "ALTERADA") + " (" + result.checked() + " registros)");

        if (result.intact()) {
            log.info("Auditoría ({}): {}", reason, result.message());
            return result;
        }

        log.error("Auditoría ({}): ¡CADENA ALTERADA! {}", reason, result.message());
        String previousId = get(ALERT_ID);
        if (String.valueOf(result.firstBrokenId()).equals(previousId)) {
            return result; // ya se avisó esta misma alteración
        }
        put(ALERT_ID, String.valueOf(result.firstBrokenId()));
        put(ALERT_MESSAGE, result.message());
        put(ALERT_DETECTED, now);
        put(ALERT_ACK, "false");
        emailAdmins(result, now);
        return result;
    }

    /** El aviso pendiente (null si no hay o si el administrador ya lo marcó como visto). */
    public Alert pendingAlert() {
        if (get(ALERT_ID) == null || "true".equals(get(ALERT_ACK))) return null;
        return new Alert(Long.valueOf(get(ALERT_ID)), get(ALERT_MESSAGE), get(ALERT_DETECTED));
    }

    /** "Entendido": deja de mostrarse el aviso (el registro de qué se alteró queda guardado). */
    public void acknowledge() {
        if (get(ALERT_ID) != null) put(ALERT_ACK, "true");
    }

    // Email a cada administrador. Si no hay cuenta de correo, queda solo el aviso del panel y el log.
    private void emailAdmins(AuditService.ChainCheck result, String detectedAt) {
        List<User> admins = userRepository.findByRoles("ADMIN");
        for (User admin : admins) {
            emailService.enviar(admin.getEmail(), "Alerta: el registro de auditoría fue alterado",
                    "email/auditoria-alerta",
                    Map.of("nombre", firstName(admin.getName()), "mensaje", result.message(),
                            "registro", result.firstBrokenId(), "fecha", detectedAt),
                    EmailType.AUDIT_ALERT, null, null);
        }
    }

    private String get(String key) {
        return settings.findById(key).map(BusinessSetting::getValue).orElse(null);
    }

    private void put(String key, String value) {
        settings.save(new BusinessSetting(key, value != null && value.length() > 500 ? value.substring(0, 500) : value));
    }

    private static String firstName(String name) {
        if (name == null || name.isBlank()) return "";
        return name.trim().split("\\s+")[0];
    }
}
