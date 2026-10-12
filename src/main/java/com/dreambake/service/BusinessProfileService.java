package com.dreambake.service;

import com.dreambake.audit.AuditText;
import com.dreambake.config.AppProperties;
import com.dreambake.entity.AuditAction;
import com.dreambake.entity.AuditModule;
import com.dreambake.entity.BusinessProfile;
import com.dreambake.entity.BusinessSetting;
import com.dreambake.repository.BusinessProfileRepository;
import com.dreambake.repository.BusinessSettingRepository;
import com.dreambake.util.InputSanitizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Datos del negocio editables desde el panel (paso 6.4): nombre, WhatsApp, email, dirección, redes, horario,
 * anticipación mínima, seña sugerida, mensaje de la tienda y pausa de pedidos.
 *
 * - get(): los datos actuales. Se guardan en memoria (caché) porque los piden todas las páginas públicas;
 *   el caché se renueva al guardar.
 * - save(): valida, guarda y deja en la auditoría el antes y el después de lo que cambió.
 * - checkStoreOrder(): reglas para aceptar un pedido de la tienda (pausa y anticipación mínima).
 */
@Service
public class BusinessProfileService {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern INSTAGRAM = Pattern.compile("^[A-Za-z0-9._]{1,30}$");
    private static final Pattern FACEBOOK = Pattern.compile("^[A-Za-z0-9.\\-]{1,80}$");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final BusinessProfileRepository repository;
    private final BusinessSettingRepository settingRepository;
    private final AppProperties app;
    private final AuditService audit;

    // Copia en memoria de la fila (volatile: todos los hilos ven la última versión)
    private volatile BusinessProfile cached;

    public BusinessProfileService(BusinessProfileRepository repository, BusinessSettingRepository settingRepository,
                                  AppProperties app, AuditService audit) {
        this.repository = repository;
        this.settingRepository = settingRepository;
        this.app = app;
        this.audit = audit;
    }

    /** Datos actuales del negocio (nunca null). Solo para leer: para cambiarlos usar save(). */
    public BusinessProfile get() {
        BusinessProfile current = cached;
        if (current == null) {
            current = repository.findById(BusinessProfile.SINGLE_ID).orElseGet(this::createFirst);
            cached = current;
        }
        return current;
    }

    // Primera vez: se crea la fila con los datos que el sistema usaba antes de este paso
    // (app.business.* de la configuración y el WhatsApp guardado en Ajustes)
    private synchronized BusinessProfile createFirst() {
        return repository.findById(BusinessProfile.SINGLE_ID).orElseGet(() -> {
            BusinessProfile p = new BusinessProfile();
            p.setName(blankToNull(app.getBusiness().getName()) != null ? app.getBusiness().getName() : "Dulce Sueños Posadas");
            String oldWhatsapp = settingRepository.findById(BusinessSettingService.WHATSAPP)
                    .map(BusinessSetting::getValue).orElse(null);
            String whatsapp = normalizeWhatsapp(oldWhatsapp);
            if (whatsapp == null) whatsapp = normalizeWhatsapp(app.getBusiness().getWhatsapp());
            p.setWhatsapp(whatsapp != null ? whatsapp : BusinessSettingService.DEFAULT_WHATSAPP);
            p.setEmail(blankToNull(app.getBusiness().getEmail()));
            p.setAddress("Posadas, Misiones");
            p.setInstagram("dulcessuenos_posadas");   // el que estaba escrito en el header, el footer y la home
            p.setMinLeadDays(3);
            p.setDepositPercent(50);
            p.setOrdersPaused(false);
            p.setUpdatedAt(LocalDateTime.now());
            return repository.save(p);
        });
    }

    /** Lo que manda el panel al guardar. */
    public record Request(String name, String whatsapp, String email, String address, String instagram,
                          String facebook, String openingHours, Integer minLeadDays, Integer depositPercent,
                          String storeBanner, Boolean ordersPaused) {
    }

    /**
     * Valida y guarda. Devuelve los errores por campo (vacío = se guardó).
     * Cada cambio queda en la auditoría con el antes y el después (solo los campos que cambiaron).
     */
    @Transactional
    public Map<String, String> save(Request r) {
        Map<String, String> errors = new LinkedHashMap<>();

        String name = clean(r.name(), 80);
        if (name == null || name.length() < 2) errors.put("name", "Escribí el nombre del negocio.");

        String whatsapp = normalizeWhatsapp(r.whatsapp());
        if (whatsapp == null) {
            errors.put("whatsapp", "Escribí el celular con código de área, sin el 0 ni el 15 (ej: 3764 159312). Se guarda como 549 + el número.");
        }

        String email = clean(r.email(), 150);
        if (email != null && !EMAIL.matcher(email).matches()) errors.put("email", "Revisá el email (por ejemplo dulcesuenos@gmail.com).");

        String instagram = socialHandle(r.instagram(), "instagram.com/");
        if (instagram != null && !INSTAGRAM.matcher(instagram).matches()) {
            errors.put("instagram", "Escribí solo el usuario de Instagram (letras, números, punto o guion bajo), sin la @.");
        }
        String facebook = socialHandle(r.facebook(), "facebook.com/");
        if (facebook != null && !FACEBOOK.matcher(facebook).matches()) {
            errors.put("facebook", "Escribí el usuario de la página de Facebook (lo que va después de facebook.com/).");
        }

        Integer lead = r.minLeadDays();
        if (lead == null || lead < 0 || lead > 60) errors.put("minLeadDays", "La anticipación tiene que ser de 0 a 60 días.");
        Integer deposit = r.depositPercent();
        if (deposit == null || deposit < 0 || deposit > 100) errors.put("depositPercent", "La seña tiene que ser un porcentaje de 0 a 100.");

        if (!errors.isEmpty()) return errors;

        BusinessProfile p = repository.findById(BusinessProfile.SINGLE_ID).orElseGet(this::createFirst);
        Map<String, Object> before = snapshot(p);

        p.setName(name);
        p.setWhatsapp(whatsapp);
        p.setEmail(email);
        p.setAddress(clean(r.address(), 200));
        p.setInstagram(instagram);
        p.setFacebook(facebook);
        p.setOpeningHours(clean(r.openingHours(), 200));
        p.setMinLeadDays(lead);
        p.setDepositPercent(deposit);
        p.setStoreBanner(clean(r.storeBanner(), 200));
        p.setOrdersPaused(Boolean.TRUE.equals(r.ordersPaused()));
        p.setUpdatedAt(LocalDateTime.now());
        BusinessProfile saved = repository.save(p);
        cached = saved;

        // El WhatsApp también queda en la tabla vieja (business_settings) por compatibilidad
        settingRepository.save(new BusinessSetting(BusinessSettingService.WHATSAPP, whatsapp));

        Map<String, Object> diff = AuditText.diff(before, snapshot(saved));
        @SuppressWarnings("unchecked")
        Map<String, Object> changed = (Map<String, Object>) diff.get("despues");
        if (!changed.isEmpty()) {
            String what;
            if (changed.size() == 1 && changed.containsKey("pedidosPausados")) {
                what = Boolean.TRUE.equals(saved.getOrdersPaused()) ? "Pausó los pedidos online" : "Reanudó los pedidos online";
            } else {
                what = "Cambió los datos del negocio: " + String.join(", ", changed.keySet().stream().map(BusinessProfileService::label).toList());
            }
            audit.log(AuditAction.CONFIG_CHANGE, AuditModule.SISTEMA, "Configuración", "negocio", what, diff);
        }
        return errors;
    }

    /**
     * ¿Se puede aceptar un pedido de la tienda para esa fecha? null = sí; si no, el mensaje para el cliente.
     * Lo usa OrderRestController.createOrder (solo para pedidos de la tienda: el personal puede cargar
     * pedidos aunque la tienda esté pausada o para mañana).
     */
    public String checkStoreOrder(LocalDate deliveryDate) {
        BusinessProfile p = get();
        if (Boolean.TRUE.equals(p.getOrdersPaused())) {
            return "Por ahora no estamos tomando pedidos por la web. Escribinos por WhatsApp y te contamos cuándo volvemos.";
        }
        LocalDate first = firstDeliveryDate();
        if (deliveryDate != null && deliveryDate.isBefore(first)) {
            return "Necesitamos " + p.getMinLeadDays() + " días de anticipación: elegí una fecha desde el " + first.format(DAY) + ".";
        }
        return null;
    }

    /** Primera fecha de entrega que se puede pedir en la tienda (hoy + anticipación mínima). */
    public LocalDate firstDeliveryDate() {
        return LocalDate.now().plusDays(get().getMinLeadDays());
    }

    /** Solo los datos públicos (para /api/public/settings): nada interno ni de auditoría. */
    public Map<String, Object> publicView() {
        BusinessProfile p = get();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", p.getName());
        m.put("whatsapp", p.getWhatsapp());
        m.put("whatsappDisplay", p.getWhatsappDisplay());
        m.put("email", p.getEmail());
        m.put("address", p.getAddress());
        m.put("instagram", p.getInstagram());
        m.put("instagramUrl", p.getInstagramUrl());
        m.put("facebook", p.getFacebook());
        m.put("facebookUrl", p.getFacebookUrl());
        m.put("openingHours", p.getOpeningHours());
        m.put("minLeadDays", p.getMinLeadDays());
        m.put("firstDeliveryDate", firstDeliveryDate().toString());
        m.put("depositPercent", p.getDepositPercent());
        m.put("storeBanner", p.getStoreBanner());
        m.put("ordersPaused", Boolean.TRUE.equals(p.getOrdersPaused()));
        return m;
    }

    /**
     * Lleva el WhatsApp al formato 549 + 10 dígitos. Acepta lo que la gente suele escribir:
     * "+54 9 376 415-9312", "3764 159312", "54 3764159312" (sin el 9). null si no se puede.
     */
    public static String normalizeWhatsapp(String raw) {
        if (raw == null) return null;
        String d = raw.replaceAll("[^0-9]", "");
        if (d.length() == 10) d = "549" + d;                                  // solo área + número
        else if (d.length() == 12 && d.startsWith("54")) d = "549" + d.substring(2);   // faltó el 9
        return d.matches("549[0-9]{10}") ? d : null;
    }

    // Usuario de una red social: acepta "@usuario", "usuario" o el link completo
    private static String socialHandle(String raw, String domain) {
        String v = clean(raw, 200);
        if (v == null) return null;
        int i = v.toLowerCase().indexOf(domain);
        if (i >= 0) v = v.substring(i + domain.length());
        v = v.replaceAll("^@", "").replaceAll("[/?#].*$", "");
        return v.isBlank() ? null : v;
    }

    // Texto sin código HTML, recortado y vacío → null
    private static String clean(String raw, int max) {
        return blankToNull(InputSanitizer.clean(raw, max));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    // "Foto" de los datos para comparar antes y después (claves en español, como el resto de la auditoría)
    private static Map<String, Object> snapshot(BusinessProfile p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nombre", p.getName());
        m.put("whatsapp", p.getWhatsapp());
        m.put("email", p.getEmail());
        m.put("direccion", p.getAddress());
        m.put("instagram", p.getInstagram());
        m.put("facebook", p.getFacebook());
        m.put("horario", p.getOpeningHours());
        m.put("anticipacionDias", p.getMinLeadDays());
        m.put("senaPorcentaje", p.getDepositPercent());
        m.put("mensajeTienda", p.getStoreBanner());
        m.put("pedidosPausados", Boolean.TRUE.equals(p.getOrdersPaused()));
        return m;
    }

    private static String label(String key) {
        return switch (key) {
            case "nombre" -> "nombre";
            case "whatsapp" -> "WhatsApp";
            case "email" -> "email";
            case "direccion" -> "dirección";
            case "horario" -> "horario";
            case "anticipacionDias" -> "días de anticipación";
            case "senaPorcentaje" -> "seña sugerida";
            case "mensajeTienda" -> "mensaje de la tienda";
            case "pedidosPausados" -> "pausa de pedidos";
            default -> key;
        };
    }
}
