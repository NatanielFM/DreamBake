package com.dreambake.service;

import com.dreambake.config.AppProperties;
import com.dreambake.entity.Order;
import com.dreambake.entity.OrderItem;
import com.dreambake.entity.OrderStatus;
import com.dreambake.entity.TrackingCodes;
import com.dreambake.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Seguimiento de pedidos para el cliente (paso 6.5): con el código (DB-7K3P9Q) se ve en qué estado está
 * el pedido, sin escribir por WhatsApp.
 *
 * Privacidad (Ley 25.326): la página es pública, así que NUNCA muestra datos personales completos:
 * el nombre va abreviado ("María G."), sin teléfono, email, dirección ni montos.
 */
@Service
public class TrackingService {

    private static final Locale AR = Locale.forLanguageTag("es-AR");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", AR);
    private static final int MAX_PER_MINUTE = 10;
    private static final long MINUTE_MS = 60_000;

    private final OrderRepository orderRepository;
    private final AppProperties app;

    // Consultas recientes por IP (en memoria; se reinician con la aplicación, como RateLimitFilter)
    private final Map<String, Deque<Long>> lookups = new ConcurrentHashMap<>();

    public TrackingService(OrderRepository orderRepository, AppProperties app) {
        this.orderRepository = orderRepository;
        this.app = app;
    }

    /** Un paso de la línea de tiempo. state: "done" (ya pasó), "current" (está acá) o "todo" (falta). */
    public record Step(String label, String hint, String state) {}

    /** Lo que ve el cliente. Sin datos personales completos. */
    public record View(String code, String shortName, String statusLabel, boolean cancelled, List<Step> steps,
                       String deliveryDay, String shift, String method, List<String> items) {}

    /** Link de seguimiento de un pedido (para los emails y la pantalla de éxito). */
    public String trackingUrl(String code) {
        return app.getPublicUrl() + "/seguimiento?codigo=" + code;
    }

    /**
     * Límite de 10 consultas por minuto por IP: registra la consulta y dice si se pasó.
     * Con 887 millones de códigos posibles y 10 intentos por minuto, adivinar uno al azar es imposible.
     */
    public boolean tooManyLookups(String ip) {
        long now = System.currentTimeMillis();
        if (lookups.size() > 10_000) lookups.entrySet().removeIf(e -> e.getValue().isEmpty() || now - e.getValue().peekLast() > MINUTE_MS);
        Deque<Long> times = lookups.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && now - times.peekFirst() > MINUTE_MS) times.pollFirst();
            if (times.size() >= MAX_PER_MINUTE) return true;
            times.addLast(now);
            return false;
        }
    }

    /** Busca el pedido por código (acepta minúsculas, espacios o sin "DB-"). Vacío si no existe. */
    @Transactional(readOnly = true)
    public Optional<View> find(String rawCode) {
        String code = TrackingCodes.normalize(rawCode);
        if (code == null) return Optional.empty();
        return orderRepository.findByTrackingCode(code).map(this::view);
    }

    private View view(Order o) {
        OrderStatus s = o.getStatus() == null ? OrderStatus.PENDING : o.getStatus();
        boolean cancelled = s == OrderStatus.CANCELLED || s == OrderStatus.REJECTED;
        int current = switch (s) {
            case PENDING -> 0;
            case CONFIRMED -> 1;
            case IN_PROGRESS -> 2;
            case COMPLETED -> 3;
            default -> -1;
        };
        String[][] labels = {
                {"Recibido", "Tenemos tu pedido. Para confirmarlo coordinamos la seña por WhatsApp."},
                {"Confirmado", "Recibimos la seña: tu fecha quedó reservada."},
                {"En preparación", "Estamos horneando y decorando."},
                {"Listo", "Tu pedido está listo o ya fue entregado."}
        };
        List<Step> steps = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            String state = cancelled ? "todo" : (i < current ? "done" : (i == current ? "current" : "todo"));
            steps.add(new Step(labels[i][0], labels[i][1], state));
        }
        String statusLabel = cancelled ? (s == OrderStatus.CANCELLED ? "Cancelado" : "No pudimos tomarlo")
                : labels[current][0];

        String day = o.getDeliveryDate() == null ? "A coordinar" : capitalize(o.getDeliveryDate().format(DAY));
        String method = "Envío".equals(o.getDeliveryMethod()) ? "Envío a domicilio" : "Retiro en el local";
        List<String> items = new ArrayList<>();
        if (o.getItems() != null) {
            for (OrderItem i : o.getItems()) items.add(i.getProductType() == null ? "Producto" : i.getProductType());
        }
        return new View(o.getTrackingCode(), shortName(o.getCustomerName()), statusLabel, cancelled, steps, day,
                o.getDeliveryShift() == null ? "" : o.getDeliveryShift(), method, items);
    }

    /** "María Gómez Pérez" → "María P."; un solo nombre queda igual; vacío → "Cliente". */
    static String shortName(String name) {
        if (name == null || name.isBlank()) return "Cliente";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) return parts[0];
        return parts[0] + " " + parts[parts.length - 1].substring(0, 1).toUpperCase(AR) + ".";
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
