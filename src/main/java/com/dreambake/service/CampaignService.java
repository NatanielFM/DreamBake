package com.dreambake.service;

import com.dreambake.config.AppProperties;
import com.dreambake.entity.*;
import com.dreambake.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Reglas de las campañas de promociones: a quién le llegan, qué se puede escribir,
 * cómo se arma el email y cuándo se puede empezar a enviar.
 * El envío en sí (de a uno, con pausa y tope diario) lo hace CampaignSender.
 */
@Service
public class CampaignService {

    // Pedidos que cuentan como compra (los mismos que en CustomerRestController)
    public static final List<OrderStatus> PURCHASE_STATUSES =
            List.of(OrderStatus.CONFIRMED, OrderStatus.IN_PROGRESS, OrderStatus.COMPLETED);

    // Campañas que todavía no terminaron (no se puede borrar el cupón o la etiqueta que usan)
    public static final List<CampaignStatus> UNFINISHED =
            List.of(CampaignStatus.DRAFT, CampaignStatus.SCHEDULED, CampaignStatus.SENDING);

    // Días sin comprar para la audiencia "inactivos"
    public static final int INACTIVE_DAYS = 90;

    // Largos máximos de lo que escribe el administrador
    private static final int MAX_NAME = 120, MAX_SUBJECT = 150, MAX_TITLE = 150, MAX_TEXT = 5000,
            MAX_BUTTON = 40, MAX_URL = 500;

    private static final DateTimeFormatter DATE_AR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private CustomerTagRepository tagRepository;
    @Autowired private CouponRepository couponRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private GalleryImageRepository galleryRepository;
    @Autowired private CouponService couponService;
    @Autowired private EmailService emailService;
    @Autowired private AppProperties app;
    @Autowired private CampaignSender sender;

    // Lo que manda el formulario del panel (texto plano; nunca HTML)
    public record Content(String name, String subject, String title, String bodyText, String imageUrl,
                          String buttonText, String buttonUrl, CampaignAudience audience, Long tagId, Long couponId) {}

    // ================= CONTENIDO =================

    /**
     * Valida lo que escribió el administrador y lo copia a la campaña.
     * Devuelve null si está todo bien, o el mensaje de error para mostrar.
     */
    public String apply(Campaign c, Content in) {
        String name = clean(in.name(), MAX_NAME);
        String subject = clean(in.subject(), MAX_SUBJECT);
        String title = clean(in.title(), MAX_TITLE);
        String text = cleanMultiline(in.bodyText(), MAX_TEXT);
        String buttonText = clean(in.buttonText(), MAX_BUTTON);
        String buttonUrl = clean(in.buttonUrl(), MAX_URL);

        if (name.isEmpty()) return "Poné un nombre a la campaña (solo lo ves vos).";
        if (subject.isEmpty()) return "Escribí el asunto del email.";
        if (title.isEmpty() && text.isEmpty()) return "Escribí un título o un texto para el email.";
        // El link del botón: solo páginas web (http/https) o una página de la tienda ("/products").
        // Así nadie puede poner un "javascript:..." que ejecute código al tocarlo.
        if (!buttonUrl.isEmpty() && !isSafeUrl(buttonUrl)) {
            return "El link del botón tiene que empezar con https:// (o ser una página de la tienda, como /products).";
        }
        if (!buttonUrl.isEmpty() && buttonText.isEmpty()) return "Escribí el texto del botón (por ejemplo: \"Ver promos\").";
        // La foto: solo una de la galería (no cualquier dirección)
        String imageUrl = clean(in.imageUrl(), MAX_URL);
        if (!imageUrl.isEmpty() && !galleryRepository.existsByUrl(imageUrl)) {
            return "La foto elegida ya no está en la galería. Elegí otra.";
        }

        CampaignAudience audience = in.audience() == null ? CampaignAudience.ALL_CONSENTED : in.audience();
        CustomerTag tag = null;
        if (audience == CampaignAudience.TAG) {
            if (in.tagId() == null) return "Elegí la etiqueta de los clientes que van a recibir la campaña.";
            tag = tagRepository.findById(in.tagId()).orElse(null);
            if (tag == null) return "La etiqueta elegida ya no existe.";
        }
        Coupon coupon = null;
        if (in.couponId() != null) {
            coupon = couponRepository.findById(in.couponId()).orElse(null);
            if (coupon == null) return "El cupón elegido ya no existe.";
        }

        c.setName(name);
        c.setSubject(subject);
        c.setTitle(title);
        c.setBodyText(text);
        c.setImageUrl(imageUrl.isEmpty() ? null : imageUrl);
        c.setButtonText(buttonText.isEmpty() ? null : buttonText);
        c.setButtonUrl(buttonUrl.isEmpty() ? null : buttonUrl);
        c.setAudience(audience);
        c.setTag(tag);
        c.setCoupon(coupon);
        return null;
    }

    private static boolean isSafeUrl(String url) {
        String u = url.toLowerCase(Locale.ROOT);
        return u.startsWith("https://") || u.startsWith("http://") || (u.startsWith("/") && !u.startsWith("//"));
    }

    // Saca espacios de más y caracteres invisibles, y corta al largo máximo
    private static String clean(String value, int max) {
        if (value == null) return "";
        String v = value.replaceAll("[\\p{Cntrl}]", " ").trim();
        return v.length() > max ? v.substring(0, max) : v;
    }

    // Igual, pero conserva los saltos de línea (el texto se arma en párrafos)
    private static String cleanMultiline(String value, int max) {
        if (value == null) return "";
        String v = value.replace("\r\n", "\n").replaceAll("[\\p{Cntrl}&&[^\n]]", " ").trim();
        return v.length() > max ? v.substring(0, max) : v;
    }

    // ================= AUDIENCIA =================

    /**
     * Clientes que reciben la campaña: siempre con permiso para promociones y con email,
     * y además según la audiencia elegida.
     */
    public List<Customer> audience(CampaignAudience audience, CustomerTag tag) {
        List<Customer> consented = customerRepository.findByMarketingConsentTrue().stream()
                .filter(c -> c.getEmail() != null && !c.getEmail().isBlank())
                .collect(Collectors.toList());
        LocalDate today = LocalDate.now(BirthdayService.ZONE);
        return switch (audience == null ? CampaignAudience.ALL_CONSENTED : audience) {
            case ALL_CONSENTED -> consented;
            case TAG -> tag == null ? List.of() : consented.stream()
                    .filter(c -> c.getTag() != null && c.getTag().getId().equals(tag.getId())).toList();
            case BIRTHDAY_THIS_MONTH -> consented.stream()
                    .filter(c -> c.getBirthDate() != null && c.getBirthDate().getMonthValue() == today.getMonthValue()).toList();
            case INACTIVE_90_DAYS -> {
                // Última compra de cada cliente (por la relación Pedido–Cliente o, en pedidos viejos, por teléfono)
                Map<Long, LocalDate> lastPurchase = lastPurchaseByCustomer(consented);
                LocalDate limit = today.minusDays(INACTIVE_DAYS);
                yield consented.stream()
                        .filter(c -> lastPurchase.containsKey(c.getId()) && lastPurchase.get(c.getId()).isBefore(limit))
                        .toList();
            }
        };
    }

    // Fecha de entrega del último pedido comprado (confirmado, en preparación o entregado) de cada cliente
    private Map<Long, LocalDate> lastPurchaseByCustomer(List<Customer> customers) {
        Map<Long, Customer> byId = new HashMap<>();
        Map<String, Customer> byPhone = new HashMap<>();
        for (Customer c : customers) {
            byId.put(c.getId(), c);
            if (c.getPhone() != null && !c.getPhone().isBlank()) byPhone.putIfAbsent(c.getPhone().trim(), c);
        }
        Map<Long, LocalDate> last = new HashMap<>();
        for (Order o : orderRepository.findAll()) {
            if (!PURCHASE_STATUSES.contains(o.getStatus()) || o.getDeliveryDate() == null) continue;
            Customer owner = o.getCustomer() != null ? byId.get(o.getCustomer().getId())
                    : (o.getCustomerPhone() != null ? byPhone.get(o.getCustomerPhone().trim()) : null);
            if (owner == null) continue;
            last.merge(owner.getId(), o.getDeliveryDate(), (a, b) -> a.isAfter(b) ? a : b);
        }
        return last;
    }

    // ================= ARMADO DEL EMAIL =================

    /**
     * Datos para la plantilla email/campana.html. Todo es texto: la plantilla lo muestra con
     * th:text, que lo escapa (un "<script>" se ve como texto y no se ejecuta).
     *
     * @param preview true = para la vista previa del panel (la foto se ve desde /uploads),
     *                false = para el email real (la foto va dentro del mensaje: cid:imagen)
     */
    public Map<String, Object> variables(Campaign c, boolean preview) {
        Map<String, Object> v = new HashMap<>();
        v.put("titulo", c.getTitle());
        v.put("parrafos", paragraphs(c.getBodyText()));
        if (c.getImageUrl() != null) v.put("imagenSrc", preview ? c.getImageUrl() : "cid:imagen");
        if (c.getButtonText() != null) {
            String url = c.getButtonUrl() == null ? app.getPublicUrl() + "/products"
                    : (c.getButtonUrl().startsWith("/") ? app.getPublicUrl() + c.getButtonUrl() : c.getButtonUrl());
            v.put("botonTexto", c.getButtonText());
            v.put("botonUrl", url);
        }
        Coupon coupon = c.getCoupon();
        if (coupon != null) {
            v.put("cuponCodigo", coupon.getCode());
            v.put("cuponBeneficio", benefit(coupon));
            if (coupon.getExpirationDate() != null) v.put("cuponVence", coupon.getExpirationDate().format(DATE_AR));
        }
        return v;
    }

    // Texto en párrafos (separados por una línea en blanco), cada uno con sus renglones
    private static List<List<String>> paragraphs(String text) {
        if (text == null || text.isBlank()) return List.of();
        List<List<String>> result = new ArrayList<>();
        for (String p : text.split("\\n\\s*\\n")) {
            List<String> lines = Arrays.stream(p.split("\\n")).map(String::trim).filter(l -> !l.isEmpty()).toList();
            if (!lines.isEmpty()) result.add(lines);
        }
        return result;
    }

    // Qué regala el cupón, dicho en simple
    private static String benefit(Coupon c) {
        if (c.getType() == CouponType.DESCUENTO && c.getDiscountPercentage() != null) {
            return c.getDiscountPercentage() + "% de descuento";
        }
        if (c.getType() == CouponType.ENVIO_GRATIS) return "Envío gratis";
        return c.getCustomText() != null && !c.getCustomText().isBlank() ? c.getCustomText() : "Un regalo en tu pedido";
    }

    /**
     * HTML de la vista previa: el mismo que se manda, con el logo y la foto tomados del sitio
     * (en el email real van dentro del mensaje) y un link de baja de ejemplo.
     */
    public String previewHtml(Campaign c) {
        String html = emailService.armarHtml(c.getSubject(), "email/campana", variables(c, true), "#baja");
        return html.replace("cid:logo", "/img/logo-email.png");
    }

    // Foto de la campaña como archivo (va dentro de cada email). null si no tiene o si ya no existe.
    public Resource imageResource(Campaign c) {
        if (c.getImageUrl() == null || !c.getImageUrl().startsWith("/uploads/")) return null;
        Path uploads = Paths.get("uploads").toAbsolutePath().normalize();
        Path file = uploads.resolve(c.getImageUrl().substring("/uploads/".length())).normalize();
        if (!file.startsWith(uploads)) return null;   // seguridad: nunca fuera de uploads/
        FileSystemResource r = new FileSystemResource(file);
        return r.exists() ? r : null;
    }

    // ================= ENVÍO =================

    /**
     * ¿Se puede empezar a enviar? Devuelve null si sí, o el motivo si no.
     *
     * @param checkRecipients false al programar (los destinatarios se cuentan a la hora del envío)
     */
    public String validateForSend(Campaign c, boolean checkRecipients) {
        if (c.getAudience() == CampaignAudience.TAG && c.getTag() == null) {
            return "Falta elegir la etiqueta de los clientes.";
        }
        if (c.getCoupon() != null && couponService.findUsable(c.getCoupon().getCode()).isEmpty()) {
            return "El cupón " + c.getCoupon().getCode() + " ya no sirve (está vencido, agotado o desactivado). Elegí otro o quitalo.";
        }
        if (checkRecipients) {
            if (!emailService.estaConfigurado()) {
                return EmailService.NO_CONFIGURADO;
            }
            if (audience(c.getAudience(), c.getTag()).isEmpty()) {
                return "Ningún cliente de esta audiencia aceptó recibir promociones (o no tiene email).";
            }
        }
        return null;
    }

    // Manda la campaña solo al email del administrador, para revisarla en su casilla.
    // Va sin id de campaña (null) para que la prueba no cuente en las métricas de "enviados".
    public EmailService.Resultado sendTest(Campaign c, String adminEmail) throws Exception {
        return emailService.enviar(adminEmail, "[Prueba] " + c.getSubject(), "email/campana", variables(c, false),
                EmailType.TEST, null, null, imageResource(c)).get(30, java.util.concurrent.TimeUnit.SECONDS);
    }

    // Empieza el envío: la campaña pasa a "enviándose" y CampaignSender la manda en segundo plano
    public void start(Campaign c) {
        c.setStatus(CampaignStatus.SENDING);
        c.setSentAt(LocalDateTime.now());
        c.setStatusNote(null);
        c.setNextAttemptAt(null);
        c.setTotalRecipients(audience(c.getAudience(), c.getTag()).size());
        // Copia del HTML enviado, como constancia (con un link de baja de ejemplo: cada cliente recibe el suyo)
        c.setBodyHtml(emailService.armarHtml(c.getSubject(), "email/campana", variables(c, false), "#baja"));
        campaignRepository.save(c);
        sender.launch(c.getId());
    }
}
