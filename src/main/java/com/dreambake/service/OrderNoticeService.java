package com.dreambake.service;

import com.dreambake.config.AppProperties;
import com.dreambake.entity.*;
import com.dreambake.repository.EmailLogRepository;
import com.dreambake.repository.OrderRepository;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Avisos de pedidos por email (y mensajes de WhatsApp para mandar a mano).
 *
 * Estos emails NO son publicidad: se mandan aunque el cliente no haya aceptado promociones,
 * siempre que haya dejado su email. Por eso no llevan link de baja.
 *
 *  - Pedido nuevo desde la tienda → al cliente "Recibimos tu pedido" y al negocio "Nuevo pedido".
 *  - Pedido confirmado / listo (entregado) / cancelado → un email al cliente por cada cambio.
 *  - Todos los días a las 8:00 (OrderNoticeScheduler): resumen de entregas al negocio y
 *    recordatorio al cliente el día antes de la entrega.
 *
 * No se repite el mismo aviso para el mismo pedido: antes de mandar se mira el historial
 * (EmailLog con orderId + tipo). Todo queda registrado en EmailLog.
 */
@Service
public class OrderNoticeService {

    private static final Logger log = LoggerFactory.getLogger(OrderNoticeService.class);

    private static final Locale AR = Locale.forLanguageTag("es-AR");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", AR);
    private static final DateTimeFormatter SHORT_DAY = DateTimeFormatter.ofPattern("EEEE d/M", AR);

    // Pedidos "agendados": los que hay que entregar
    private static final List<OrderStatus> SCHEDULED = List.of(OrderStatus.CONFIRMED, OrderStatus.IN_PROGRESS);

    /** Aviso de que un pedido se creó o cambió de estado (lo publica OrderRestController al guardar). */
    public record OrderChanged(Long orderId, OrderStatus status, boolean created) {}

    @Autowired private OrderRepository orderRepository;
    @Autowired private EmailLogRepository emailLogRepository;
    @Autowired private EmailService emailService;
    @Autowired private MailAccountService mailAccounts;
    @Autowired private BusinessSettingService settingService;
    @Autowired private AppProperties app;
    @Autowired private PlatformTransactionManager transactionManager;

    // Evita mandar dos veces el mismo aviso si dos cambios llegan casi juntos
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private final ReentrantLock dailyLock = new ReentrantLock();

    // ================= LO QUE SE VE EN LOS EMAILS =================
    // Datos del pedido ya "aplanados" (texto) para las plantillas. Se arman dentro de una
    // transacción, porque los productos del pedido se cargan recién cuando se piden.

    @Getter
    public static class ItemView {
        private final String name;
        private final List<String> lines;
        ItemView(String name, List<String> lines) { this.name = name; this.lines = lines; }
    }

    @Getter
    public static class OrderView {
        private Long id;
        private OrderStatus status;
        private String customerName, firstName, email, phone;
        private Customer customer;                 // puede ser null (pedidos que todavía no se confirmaron)
        private LocalDate deliveryDate;
        private String deliveryDay, deliveryDayShort, shift, method, address, couponCode;
        private boolean delivery;                  // true = envío, false = retiro
        private double totalValue, depositValue;
        private String total, deposit, balance;    // con formato: 82.000
        private boolean hasDeposit;
        private List<ItemView> items = new ArrayList<>();
        private String statusLabel;
    }

    @Getter
    public static class DayView {
        private final String label;
        private final boolean highlight;           // "mañana" va destacado
        private final List<OrderView> orders;
        DayView(String label, boolean highlight, List<OrderView> orders) {
            this.label = label; this.highlight = highlight; this.orders = orders;
        }
    }

    private static String money(double v) {
        return String.format(AR, "%,.0f", v);
    }

    private static String capitalize(String s) {
        return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String statusLabel(OrderStatus s) {
        return switch (s) {
            case PENDING -> "Nuevo, sin confirmar";
            case CONFIRMED -> "Agendado";
            case IN_PROGRESS -> "En preparación";
            case COMPLETED -> "Entregado";
            case CANCELLED -> "Cancelado";
            default -> "Rechazado";
        };
    }

    // Convierte un pedido en datos simples para las plantillas (llamar dentro de una transacción)
    private OrderView view(Order o) {
        OrderView v = new OrderView();
        v.id = o.getId();
        v.status = o.getStatus();
        v.statusLabel = statusLabel(o.getStatus());
        v.customerName = o.getCustomerName() == null || o.getCustomerName().isBlank() ? "Cliente" : o.getCustomerName().trim();
        v.firstName = v.customerName.split("\\s+")[0];
        v.email = o.getCustomerEmail() == null ? "" : o.getCustomerEmail().trim();
        v.phone = o.getCustomerPhone() == null ? "" : o.getCustomerPhone().trim();
        v.customer = o.getCustomer();
        v.deliveryDate = o.getDeliveryDate();
        v.deliveryDay = o.getDeliveryDate() == null ? "a coordinar" : capitalize(o.getDeliveryDate().format(DAY));
        v.deliveryDayShort = o.getDeliveryDate() == null ? "" : capitalize(o.getDeliveryDate().format(SHORT_DAY));
        v.shift = o.getDeliveryShift() == null ? "" : o.getDeliveryShift().trim();
        v.delivery = "Envío".equals(o.getDeliveryMethod());
        v.method = v.delivery ? "Envío a domicilio" : "Retiro en el local";
        v.address = v.delivery && o.getDeliveryAddress() != null ? o.getDeliveryAddress().trim() : "";
        v.couponCode = o.getCouponCode();
        v.totalValue = o.getTotalPrice() == null ? 0 : o.getTotalPrice();
        v.depositValue = o.getDeposit() == null ? 0 : o.getDeposit();
        v.total = money(v.totalValue);
        v.deposit = money(v.depositValue);
        v.balance = money(Math.max(0, v.totalValue - v.depositValue));
        v.hasDeposit = v.depositValue > 0;
        if (o.getItems() != null) {
            for (OrderItem i : o.getItems()) {
                List<String> lines = new ArrayList<>();
                if (i.getDetails() != null) {
                    for (String l : i.getDetails().split("\\r?\\n")) {
                        // Los detalles vienen del carrito con marcas tipo "> *Aclaraciones:*": se dejan como texto simple
                        String t = l.replaceAll("^>\\s*", "").replace("*", "").trim();
                        if (!t.isEmpty()) lines.add(t);
                    }
                }
                v.items.add(new ItemView(i.getProductType() == null ? "Producto" : i.getProductType(), lines));
            }
        }
        return v;
    }

    // Lee un pedido y lo convierte en OrderView (en su propia transacción de solo lectura)
    private OrderView load(Long orderId) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setReadOnly(true);
        return tx.execute(s -> orderRepository.findById(orderId).map(this::view).orElse(null));
    }

    // ================= AVISOS POR CAMBIO DE ESTADO =================

    /**
     * Se ejecuta DESPUÉS de guardar el pedido (AFTER_COMMIT: si el guardado falla, no se avisa nada)
     * y en otro hilo, para que la pantalla no espere a los emails.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderChanged(OrderChanged event) {
        try {
            if (!emailService.estaConfigurado()) {
                log.info("Pedido #{}: no se mandan avisos por email porque no hay cuenta de correo configurada.", event.orderId());
                return;
            }
            OrderView v = load(event.orderId());
            if (v == null) return;
            if (event.created()) {
                sendToCustomer(v, EmailType.ORDER_RECEIVED, "Recibimos tu pedido #" + v.id, "email/pedidorecibido",
                        Map.of());
                sendToBusiness(v, EmailType.NEW_ORDER_ALERT, "Nuevo pedido #" + v.id + " de " + v.customerName,
                        "email/nuevopedido", Map.of("panelUrl", app.getPublicUrl() + "/admin"));
                return;
            }
            switch (event.status()) {
                case CONFIRMED -> sendToCustomer(v, EmailType.ORDER_CONFIRMATION, "Tu pedido #" + v.id + " está confirmado",
                        "email/pedidoestado", Map.of("tipo", "confirmado"));
                case COMPLETED -> sendToCustomer(v, EmailType.ORDER_READY, "Tu pedido #" + v.id + " está listo",
                        "email/pedidoestado", Map.of("tipo", "listo"));
                case CANCELLED -> sendToCustomer(v, EmailType.ORDER_CANCELLED, "Tu pedido #" + v.id + " fue cancelado",
                        "email/pedidoestado", Map.of("tipo", "cancelado"));
                default -> { /* los demás estados no mandan email */ }
            }
        } catch (Exception e) {
            log.error("Pedido #{}: no se pudieron mandar los avisos: {}", event.orderId(), e.getMessage());
        }
    }

    private static boolean validEmail(String e) {
        return e != null && e.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") && e.length() <= 150;
    }

    // Variables comunes de las plantillas de pedidos
    private Map<String, Object> vars(OrderView v, Map<String, Object> extra) {
        Map<String, Object> m = new HashMap<>(extra);
        m.put("p", v);
        m.put("whatsapp", settingService.getWhatsappNumber());   // el del panel (Ajustes), para consultas
        return m;
    }

    /**
     * Manda un aviso al cliente si dejó un email válido y todavía no se le mandó ese mismo aviso.
     * Devuelve true si se mandó.
     */
    private boolean sendToCustomer(OrderView v, EmailType type, String subject, String template, Map<String, Object> extra) {
        if (!validEmail(v.email)) return false;
        return sendOnce(v, type, v.email, subject, template, vars(v, extra), v.customer);
    }

    // Lo mismo, pero al email del negocio (si no hay uno cargado, a la cuenta desde la que se manda)
    private boolean sendToBusiness(OrderView v, EmailType type, String subject, String template, Map<String, Object> extra) {
        String to = businessEmail();
        if (!validEmail(to)) {
            log.warn("Pedido #{}: no hay email del negocio para avisarle ({}).", v.id, type);
            return false;
        }
        return sendOnce(v, type, to, subject, template, vars(v, extra), null);
    }

    private String businessEmail() {
        String configured = app.getBusiness().getEmail();
        if (validEmail(configured)) return configured;
        return mailAccounts.current().map(MailAccountService.Account::username).orElse("");
    }

    // Revisa el historial, manda y espera el resultado. Un mismo (pedido, aviso) no se manda dos veces.
    private boolean sendOnce(OrderView v, EmailType type, String to, String subject, String template,
                             Map<String, Object> vars, Customer customer) {
        String key = v.id + ":" + type;
        if (!inFlight.add(key)) return false;                    // ya se está mandando
        try {
            if (emailLogRepository.existsByOrderIdAndTypeAndStatus(v.id, type, EmailStatus.SENT)) {
                log.debug("Pedido #{}: el aviso {} ya se mandó, no se repite.", v.id, type);
                return false;
            }
            EmailService.Resultado r = emailService.enviarPedido(to, subject, template, vars, type, customer, v.id)
                    .get(30, TimeUnit.SECONDS);
            return r.ok();
        } catch (Exception e) {
            log.error("Pedido #{}: no se pudo confirmar el aviso {}: {}", v.id, type, e.getMessage());
            return false;
        } finally {
            inFlight.remove(key);
        }
    }

    // ================= TAREA DIARIA (8:00) =================

    public record DailySummary(int reminders, boolean summarySent, String message) {}

    /**
     * Recordatorio al cliente de los pedidos que se entregan MAÑANA y resumen al negocio de
     * las entregas de los próximos 3 días. Se puede correr varias veces: no repite lo que ya salió.
     */
    public DailySummary runDaily(String origin) {
        dailyLock.lock();
        try {
            if (!emailService.estaConfigurado()) {
                log.warn("Avisos de entregas ({}): no se mandó nada porque no hay cuenta de correo configurada.", origin);
                return new DailySummary(0, false, "El correo no está configurado: no se mandó ningún aviso.");
            }
            LocalDate today = LocalDate.now(BirthdayService.ZONE);
            LocalDate tomorrow = today.plusDays(1);

            // Pedidos de hoy y de los próximos 3 días (confirmados, en preparación y sin confirmar)
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.setReadOnly(true);
            List<OrderView> upcoming = tx.execute(s -> orderRepository
                    .findByDeliveryDateBetweenAndStatusIn(today, today.plusDays(3),
                            List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.IN_PROGRESS)).stream()
                    .map(this::view).toList());
            if (upcoming == null) upcoming = List.of();

            // 1) Recordatorio al cliente: el día anterior a la entrega
            int reminders = 0;
            for (OrderView v : upcoming) {
                if (!tomorrow.equals(v.deliveryDate) || !SCHEDULED.contains(v.status)) continue;
                if (sendToCustomer(v, EmailType.ORDER_REMINDER, "Mañana entregamos tu pedido #" + v.id,
                        "email/recordatoriopedido", Map.of())) reminders++;
            }

            // 2) Resumen al negocio (una vez por día, y solo si hay algo para avisar)
            boolean summarySent = false;
            boolean alreadySent = emailLogRepository.existsByTypeAndStatusAndSentAtBetween(EmailType.DELIVERY_SUMMARY,
                    EmailStatus.SENT, today.atStartOfDay(), tomorrow.atStartOfDay());
            String message;
            if (alreadySent) {
                message = "El resumen de hoy ya se mandó.";
            } else if (upcoming.isEmpty()) {
                message = "No hay entregas en los próximos 3 días: no se mandó resumen.";
            } else {
                summarySent = sendSummary(today, upcoming);
                message = summarySent ? "Resumen enviado al negocio." : "No se pudo mandar el resumen al negocio.";
            }
            log.info("Avisos de entregas ({}): {} recordatorios a clientes. {}", origin, reminders, message);
            return new DailySummary(reminders, summarySent, reminders + " recordatorio(s) a clientes. " + message);
        } finally {
            dailyLock.unlock();
        }
    }

    // Arma y manda el email al negocio: pedidos de hoy y próximos 3 días, el de mañana destacado
    private boolean sendSummary(LocalDate today, List<OrderView> upcoming) {
        String to = businessEmail();
        if (!validEmail(to)) {
            log.warn("Resumen de entregas: no hay email del negocio al que mandarlo.");
            return false;
        }
        List<OrderView> scheduled = upcoming.stream().filter(o -> SCHEDULED.contains(o.status)).toList();
        List<OrderView> pending = upcoming.stream().filter(o -> o.status == OrderStatus.PENDING).toList();

        List<DayView> days = new ArrayList<>();
        int tomorrowCount = 0;
        for (int i = 0; i <= 3; i++) {
            LocalDate d = today.plusDays(i);
            List<OrderView> list = scheduled.stream().filter(o -> d.equals(o.deliveryDate))
                    .sorted(Comparator.comparing(o -> o.shift)).toList();
            if (list.isEmpty()) continue;
            String label = i == 0 ? "Hoy" : i == 1 ? "Mañana" : "En " + i + " días";
            days.add(new DayView(label + " · " + capitalize(d.format(DAY)), i == 1, list));
            if (i == 1) tomorrowCount = list.size();
        }
        Map<String, Object> vars = new HashMap<>();
        vars.put("dias", days);
        vars.put("sinConfirmar", pending);
        vars.put("total", scheduled.size());
        vars.put("panelUrl", app.getPublicUrl() + "/admin");
        vars.put("whatsapp", settingService.getWhatsappNumber());
        String subject = "Entregas de los próximos días: " + scheduled.size()
                + (scheduled.size() == 1 ? " pedido" : " pedidos")
                + (tomorrowCount > 0 ? " (" + tomorrowCount + " para mañana)" : "")
                + (pending.isEmpty() ? "" : " + " + pending.size() + " sin confirmar");
        try {
            return emailService.enviarPedido(to, subject, "email/resumenentregas", vars, EmailType.DELIVERY_SUMMARY, null, null)
                    .get(30, TimeUnit.SECONDS).ok();
        } catch (Exception e) {
            log.error("Resumen de entregas: no se pudo confirmar el envío: {}", e.getMessage());
            return false;
        }
    }

    // ================= WHATSAPP (botón "Avisar por WhatsApp" de la ficha del pedido) =================

    public record WhatsappNotice(String url, String message) {}

    /** Mensaje de WhatsApp según el estado actual del pedido, con el link wa.me al número del cliente. */
    public Optional<WhatsappNotice> whatsappFor(Long orderId) {
        OrderView v = load(orderId);
        if (v == null) return Optional.empty();
        String number = BirthdayService.toWhatsappNumber(v.phone);
        if (number == null) return Optional.empty();
        String business = app.getBusiness().getName();
        // En medio de una frase el día va en minúscula ("para el sábado 10 de octubre")
        String when = v.deliveryDay.toLowerCase(AR) + (v.shift.isEmpty() ? "" : " (" + v.shift.toLowerCase(AR) + ")");
        String hello = "Hola " + v.firstName + ", ";
        String text = switch (v.status) {
            case PENDING -> hello + "recibimos tu pedido #" + v.id + " en " + business + ". Entrega pedida: " + when
                    + ". Total: $" + v.total + ". En breve te lo confirmamos.";
            case CONFIRMED -> hello + "tu pedido #" + v.id + " está confirmado. Entrega: " + when + ". Total: $" + v.total
                    + (v.hasDeposit ? ", seña: $" + v.deposit + ", saldo: $" + v.balance : "") + ". ¡Gracias por elegirnos!";
            case IN_PROGRESS -> hello + "ya estamos preparando tu pedido #" + v.id + " para el " + when + ".";
            case COMPLETED -> hello + "tu pedido #" + v.id + " está listo. "
                    + (v.delivery ? "Lo llevamos a " + v.address + "." : "Ya podés pasar a retirarlo.") + " ¡Que lo disfrutes!";
            default -> hello + "tu pedido #" + v.id + " fue cancelado. Si tenés alguna consulta, escribinos por acá.";
        };
        String url = "https://wa.me/" + number + "?text=" + URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
        return Optional.of(new WhatsappNotice(url, text));
    }
}
