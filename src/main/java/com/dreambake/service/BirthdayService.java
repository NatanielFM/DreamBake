package com.dreambake.service;

import com.dreambake.config.AppProperties;
import com.dreambake.entity.Coupon;
import com.dreambake.entity.CouponType;
import com.dreambake.entity.Customer;
import com.dreambake.entity.EmailStatus;
import com.dreambake.entity.EmailType;
import com.dreambake.repository.CouponRepository;
import com.dreambake.repository.CustomerRepository;
import com.dreambake.repository.EmailLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Saludo de cumpleaños con cupón de regalo (estilo Starbucks).
 *
 * Qué hace:
 *  - Busca a los clientes que cumplen años hoy, aceptaron recibir promociones y tienen email.
 *  - A cada uno le genera un cupón único (CUMPLE-XXXX, descuento, un solo uso, válido 30 días)
 *    y le manda el email "email/cumpleanos".
 *  - No repite: si ya se le mandó el saludo este año (EmailLog tipo BIRTHDAY enviado), se saltea.
 *
 * Lo llaman LoyaltyScheduler (todos los días a las 9:00 y al arrancar la app) y el panel
 * (Clientes → Fidelización: "Enviar saludo ahora" y "Revisar cumpleaños de hoy").
 */
@Service
public class BirthdayService {

    private static final Logger log = LoggerFactory.getLogger(BirthdayService.class);

    // Hora de Argentina: "hoy" es el día de Posadas aunque el servidor esté en otra zona
    public static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    // Días de validez del cupón de regalo
    public static final int COUPON_VALID_DAYS = 30;

    // Letras y números sin confundibles (sin 0/O ni 1/I) para que el código se lea y escriba fácil
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter DATE_AR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Autowired private CustomerRepository customerRepository;
    @Autowired private CouponRepository couponRepository;
    @Autowired private EmailLogRepository emailLogRepository;
    @Autowired private EmailService emailService;
    @Autowired private BusinessSettingService settingService;
    @Autowired private AppProperties app;
    @Autowired private BusinessProfileService businessProfile;   // nombre del negocio (paso 6.4)

    // Evita que la tarea de las 9:00 y el "revisar ahora" corran a la vez y manden el saludo dos veces
    private final ReentrantLock runLock = new ReentrantLock();

    // ===== Resultados =====

    // Resultado de saludar a un cliente
    public enum Outcome { SENT, ALREADY_SENT, FAILED, NOT_ELIGIBLE }

    public record GreetResult(Outcome outcome, String message, String code) {
        public boolean ok() { return outcome == Outcome.SENT; }
    }

    // Resumen de revisar los cumpleaños de hoy
    public record RunSummary(int birthdays, int sent, int alreadySent, int failed, String message) {}

    // Una fila de la lista "próximos cumpleaños" del panel
    public record UpcomingBirthday(Long customerId, String name, LocalDate date, long daysLeft,
                                   boolean consent, boolean hasEmail, boolean hasPhone, boolean greetedThisYear) {}

    // ===== Fechas =====

    private static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    private static boolean isFeb29(LocalDate d) {
        return d.getMonthValue() == 2 && d.getDayOfMonth() == 29;
    }

    /**
     * ¿El cumpleaños de alguien nacido en "birth" se festeja en el día "day"?
     * Mismo día y mes. Los nacidos el 29/2 se festejan el 29/2 en los años bisiestos
     * y el 28/2 en los demás.
     */
    public static boolean isBirthdayOn(LocalDate birth, LocalDate day) {
        if (birth == null || day == null) return false;
        if (isFeb29(birth)) {
            return day.isLeapYear()
                    ? (day.getMonthValue() == 2 && day.getDayOfMonth() == 29)
                    : (day.getMonthValue() == 2 && day.getDayOfMonth() == 28);
        }
        return birth.getMonthValue() == day.getMonthValue() && birth.getDayOfMonth() == day.getDayOfMonth();
    }

    /** Próxima fecha de cumpleaños desde "from" (inclusive): hoy mismo cuenta. */
    public static LocalDate nextBirthday(LocalDate birth, LocalDate from) {
        LocalDate candidate = celebrationInYear(birth, from.getYear());
        if (candidate.isBefore(from)) candidate = celebrationInYear(birth, from.getYear() + 1);
        return candidate;
    }

    // Día en que se festeja en un año dado (el 29/2 pasa al 28/2 si ese año no es bisiesto)
    private static LocalDate celebrationInYear(LocalDate birth, int year) {
        if (isFeb29(birth) && !java.time.Year.isLeap(year)) return LocalDate.of(year, 2, 28);
        return LocalDate.of(year, birth.getMonthValue(), birth.getDayOfMonth());
    }

    // ===== Revisión diaria =====

    /**
     * Saluda a todos los clientes que cumplen años hoy y todavía no fueron saludados este año.
     * Si el correo no está configurado no hace nada (se reintenta la próxima vez).
     *
     * @param origin de dónde viene la orden, solo para el registro (log)
     */
    public RunSummary runToday(String origin) {
        runLock.lock();
        try {
            LocalDate today = today();
            if (!emailService.estaConfigurado()) {
                log.warn("Cumpleaños ({}): no se mandó nada porque el correo no está configurado (Ajustes → Correo de envío).", origin);
                return new RunSummary(0, 0, 0, 0, "El correo no está configurado: no se pudo mandar ningún saludo.");
            }
            int birthdays = 0, sent = 0, already = 0, failed = 0;
            for (Customer c : customerRepository.findByMarketingConsentTrue()) {
                if (!isBirthdayOn(c.getBirthDate(), today)) continue;
                if (c.getEmail() == null || c.getEmail().isBlank()) continue;
                birthdays++;
                GreetResult r = greet(c, false);
                switch (r.outcome()) {
                    case SENT -> sent++;
                    case ALREADY_SENT -> already++;
                    default -> failed++;
                }
            }
            log.info("Cumpleaños ({}): {} cumpleaños hoy, {} saludos enviados, {} ya saludados este año, {} con problemas.",
                    origin, birthdays, sent, already, failed);
            String message = birthdays == 0 ? "Hoy no cumple años ningún cliente que acepte promociones."
                    : sent + " saludo(s) enviado(s)"
                    + (already > 0 ? ", " + already + " ya saludado(s) este año" : "")
                    + (failed > 0 ? ", " + failed + " con problemas (mirá el historial del cliente)" : "") + ".";
            return new RunSummary(birthdays, sent, already, failed, message);
        } finally {
            runLock.unlock();
        }
    }

    // ===== Saludo a un cliente =====

    /**
     * Le manda el saludo de cumpleaños (con su cupón) a un cliente.
     *
     * @param force true = se manda aunque ya se le haya mandado este año (botón "Enviar saludo ahora")
     */
    public GreetResult greet(Customer customer, boolean force) {
        if (!Boolean.TRUE.equals(customer.getMarketingConsent())) {
            return new GreetResult(Outcome.NOT_ELIGIBLE, "El cliente no aceptó recibir promociones.", null);
        }
        if (customer.getEmail() == null || customer.getEmail().isBlank()) {
            return new GreetResult(Outcome.NOT_ELIGIBLE, "El cliente no tiene email cargado.", null);
        }
        if (!force && greetedThisYear(customer)) {
            return new GreetResult(Outcome.ALREADY_SENT, "Ya se le mandó el saludo de este año.", null);
        }
        Coupon coupon = findOrCreateCoupon(customer);
        String firstName = firstName(customer.getName());
        String subject = "¡Feliz cumpleaños" + (firstName == null ? "" : ", " + firstName) + "! Un regalo de "
                + businessProfile.get().getName();
        Map<String, Object> vars = new java.util.HashMap<>();
        vars.put("nombre", firstName);
        vars.put("codigo", coupon.getCode());
        vars.put("porcentaje", coupon.getDiscountPercentage());
        vars.put("vencimiento", coupon.getExpirationDate().format(DATE_AR));
        try {
            // El envío es asíncrono; acá se espera el resultado (hasta 30 s) para saber si salió bien
            EmailService.Resultado r = emailService.enviar(customer.getEmail(), subject, "email/cumpleanos", vars,
                    EmailType.BIRTHDAY, customer, null).get(30, TimeUnit.SECONDS);
            return r.ok()
                    ? new GreetResult(Outcome.SENT, "Saludo enviado a " + customer.getEmail() + ".", coupon.getCode())
                    : new GreetResult(Outcome.FAILED, r.mensaje(), coupon.getCode());
        } catch (Exception e) {
            log.error("Saludo de cumpleaños a {}: no se pudo confirmar el envío: {}", customer.getEmail(), e.getMessage());
            return new GreetResult(Outcome.FAILED, "No se pudo confirmar el envío del email.", coupon.getCode());
        }
    }

    // ¿Ya se le mandó un saludo de cumpleaños (enviado de verdad) en el año en curso?
    public boolean greetedThisYear(Customer customer) {
        int year = today().getYear();
        return emailLogRepository.existsByCustomerAndTypeAndStatusAndSentAtBetween(customer, EmailType.BIRTHDAY,
                EmailStatus.SENT, LocalDate.of(year, 1, 1).atStartOfDay(), LocalDate.of(year + 1, 1, 1).atStartOfDay());
    }

    // ===== Cupón de regalo =====

    // Los cupones de cumpleaños se identifican por su descripción: "Cumpleaños 2026 - cliente #7".
    // Así, si el email falló o se prepara el mensaje de WhatsApp después, se reutiliza el mismo cupón
    // del año en vez de crear uno nuevo cada vez.
    private String couponKey(Customer customer) {
        return "Cumpleaños " + today().getYear() + " - cliente #" + customer.getId();
    }

    /** Cupón de regalo de este año para el cliente: reutiliza el que sigue vigente o crea uno nuevo. */
    public synchronized Coupon findOrCreateCoupon(Customer customer) {
        String key = couponKey(customer);
        Optional<Coupon> existing = couponRepository.findFirstByDescriptionOrderByIdDesc(key)
                .filter(c -> c.isActive() && c.getUsageLimit() != null && c.getUsageLimit() > 0)
                .filter(c -> c.getExpirationDate() != null && !today().isAfter(c.getExpirationDate()));
        if (existing.isPresent()) return existing.get();

        Coupon coupon = new Coupon();
        coupon.setCode(newUniqueCode());
        coupon.setType(CouponType.DESCUENTO);
        coupon.setActive(true);
        coupon.setDescription(key);
        coupon.setDiscountPercentage(settingService.getBirthdayPercentage());
        coupon.setUsageLimit(1);
        coupon.setUnlimited(false);
        coupon.setExpirationDate(today().plusDays(COUPON_VALID_DAYS));
        return couponRepository.save(coupon);
    }

    // Código CUMPLE-XXXX que no exista todavía
    private String newUniqueCode() {
        for (int attempt = 0; attempt < 50; attempt++) {
            StringBuilder sb = new StringBuilder("CUMPLE-");
            for (int i = 0; i < 4; i++) sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
            if (couponRepository.findByCodeIgnoreCase(sb.toString()).isEmpty()) return sb.toString();
        }
        throw new IllegalStateException("No se pudo generar un código de cupón único.");
    }

    // ===== Próximos cumpleaños (panel) =====

    /** Clientes que cumplen años desde hoy hasta dentro de "days" días, del más cercano al más lejano. */
    public List<UpcomingBirthday> upcoming(int days) {
        LocalDate today = today();
        List<UpcomingBirthday> list = new ArrayList<>();
        for (Customer c : customerRepository.findByBirthDateIsNotNull()) {
            LocalDate next = nextBirthday(c.getBirthDate(), today);
            long left = ChronoUnit.DAYS.between(today, next);
            if (left > days) continue;
            list.add(new UpcomingBirthday(c.getId(), c.getName(), next, left,
                    Boolean.TRUE.equals(c.getMarketingConsent()),
                    c.getEmail() != null && !c.getEmail().isBlank(),
                    toWhatsappNumber(c.getPhone()) != null,
                    greetedThisYear(c)));
        }
        list.sort(Comparator.comparingLong(UpcomingBirthday::daysLeft).thenComparing(UpcomingBirthday::name));
        return list;
    }

    // ===== WhatsApp =====

    public record WhatsappGreeting(String url, String message, String code) {}

    /**
     * Arma el mensaje de cumpleaños con el cupón ya escrito y el link wa.me al número del cliente.
     * Devuelve vacío si el teléfono del cliente no sirve para WhatsApp.
     */
    public Optional<WhatsappGreeting> whatsappGreeting(Customer customer) {
        String number = toWhatsappNumber(customer.getPhone());
        if (number == null) return Optional.empty();
        Coupon coupon = findOrCreateCoupon(customer);
        String firstName = firstName(customer.getName());
        String message = "¡Feliz cumpleaños" + (firstName == null ? "" : ", " + firstName) + "! 🎂 "
                + "Desde " + businessProfile.get().getName() + " queremos regalarte un "
                + coupon.getDiscountPercentage() + "% de descuento en tu próximo pedido. "
                + "Usá el código " + coupon.getCode() + " en nuestra tienda "
                + "(válido hasta el " + coupon.getExpirationDate().format(DATE_AR) + "): " + app.getPublicUrl();
        String url = "https://wa.me/" + number + "?text=" + URLEncoder.encode(message, StandardCharsets.UTF_8).replace("+", "%20");
        return Optional.of(new WhatsappGreeting(url, message, coupon.getCode()));
    }

    /**
     * Pasa un teléfono argentino escrito como lo escribe la gente al formato de WhatsApp
     * (549 + área + número, solo dígitos). Devuelve null si no se puede interpretar.
     * Ejemplos: "3764 154812" → 5493764154812 · "0376 15 4154812" → 5493764154812 · "+54 9 376 4154812" → igual.
     */
    public static String toWhatsappNumber(String phone) {
        if (phone == null) return null;
        String digits = phone.replaceAll("\\D", "");
        if (digits.isEmpty()) return null;
        // Ya viene con el código de país (54)
        if (digits.startsWith("54") && digits.length() >= 12) {
            if (digits.startsWith("549") && digits.length() == 13) return digits;
            if (!digits.startsWith("549") && digits.length() == 12) return "549" + digits.substring(2);
            return null;
        }
        // Forma local de siempre: 0 adelante del área y 15 adelante del número (0376 15 4154812)
        if (digits.startsWith("0")) digits = digits.substring(1);
        if (digits.length() == 12) {
            int pos = digits.indexOf("15", 2);
            if (pos >= 2 && pos <= 4) digits = digits.substring(0, pos) + digits.substring(pos + 2);
        }
        return digits.length() == 10 ? "549" + digits : null;
    }

    // Solo el primer nombre ("Ana" en vez de "Ana María Pérez")
    private static String firstName(String name) {
        if (name == null || name.isBlank()) return null;
        return name.trim().split("\\s+")[0];
    }
}
