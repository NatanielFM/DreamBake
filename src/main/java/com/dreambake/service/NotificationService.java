package com.dreambake.service;

import com.dreambake.audit.AuditText;
import com.dreambake.entity.*;
import com.dreambake.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Avisos de la campanita de los paneles (paso 6.6).
 *
 * Los avisos se CALCULAN cada vez que se piden, a partir de los datos actuales; no hay una tabla de avisos.
 * Cada uno tiene una clave que cambia cuando cambia la situación, así un aviso leído vuelve a aparecer
 * si el problema se repite (ej: "stock:3:2.0" — si el stock baja otra vez, la clave es otra).
 * Lo único que se guarda es qué claves leyó cada persona (NotificationRead).
 *
 * Tipos y quién los ve:
 *   ORDER     pedido nuevo sin confirmar ........ los dos (el pastelero sin montos)
 *   STOCK     insumo en el mínimo o menos ....... los dos
 *   EXPIRING  insumo que vence en 7 días o menos  los dos
 *   TOMORROW  entregas de mañana ................ los dos
 *   REMINDER  recordatorio propio vencido ....... los dos (cada uno los suyos)
 *   BACKUP    la última copia de seguridad falló  solo ADMIN
 *   BIRTHDAY  cumpleaños de clientes hoy ........ solo ADMIN
 */
@Service
public class NotificationService {

    private static final int EXPIRING_DAYS = 7;
    private static final int MAX_ITEMS = 50;
    private static final Pattern KEY = Pattern.compile("^[a-z]+:[A-Za-z0-9:._-]{1,70}$");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("dd/MM HH:mm");
    private static final List<OrderStatus> ACTIVE = List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.IN_PROGRESS);

    private final OrderRepository orderRepository;
    private final InventoryItemRepository inventoryRepository;
    private final ReminderRepository reminderRepository;
    private final CustomerRepository customerRepository;
    private final BackupService backupService;
    private final NotificationReadRepository readRepository;

    public NotificationService(OrderRepository orderRepository, InventoryItemRepository inventoryRepository,
                               ReminderRepository reminderRepository, CustomerRepository customerRepository,
                               BackupService backupService, NotificationReadRepository readRepository) {
        this.orderRepository = orderRepository;
        this.inventoryRepository = inventoryRepository;
        this.reminderRepository = reminderRepository;
        this.customerRepository = customerRepository;
        this.backupService = backupService;
        this.readRepository = readRepository;
    }

    /**
     * Un aviso. tab = pestaña del panel que se abre al tocarlo ("pedidos/nuevos" = pestaña Pedidos, solapa Nuevos). sound = true solo para pedidos nuevos
     * (el panel puede hacer un sonido suave si la persona lo activó).
     */
    public record Item(String key, String type, String title, String text, String tab, boolean read, boolean sound) {}

    public record Result(int unread, List<Item> items) {}

    /** Avisos de esta persona según su rol (admin = tiene el permiso ADMIN en la sesión), con los leídos marcados. */
    @Transactional(readOnly = true)
    public Result forUser(User user, boolean admin) {
        LocalDate today = LocalDate.now();
        List<Item> items = new ArrayList<>();

        // 1. Backup fallido (solo admin): el último intento salió mal
        if (admin) {
            backupService.lastAttempt()
                    .filter(b -> b.getStatus() == BackupStatus.FAILED)
                    .ifPresent(b -> items.add(item("backup:" + b.getId(), "BACKUP", "Falló la copia de seguridad",
                            "Del " + b.getCreatedAt().format(HOUR) + ". Mirá la pestaña Backups.", "backups", false)));
        }

        // 2. Pedidos nuevos sin confirmar (los más nuevos primero)
        List<Order> pending = new ArrayList<>(orderRepository.findByStatus(OrderStatus.PENDING));
        pending.sort(Comparator.comparing(Order::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        for (Order o : pending) {
            String when = o.getDeliveryDate() == null ? "" : " · para el " + o.getDeliveryDate().format(DAY);
            String money = admin ? " · " + AuditText.money(o.getTotalPrice()) : "";   // al pastelero nunca montos
            items.add(item("order:" + o.getId(), "ORDER", "Pedido nuevo #" + o.getId(),
                    name(o.getCustomerName()) + when + money, "pedidos/nuevos", true));
        }

        // 3. Recordatorios propios que ya vencieron y no están hechos
        for (Reminder r : reminderRepository.findByOwnerAndDoneFalseAndDueAtLessThanEqualOrderByDueAt(user, LocalDateTime.now())) {
            items.add(item("reminder:" + r.getId(), "REMINDER", "Recordatorio vencido",
                    r.getTitle() + " (" + r.getDueAt().format(HOUR) + ")", admin ? "resumen" : "pedidos", false));
        }

        // 4. Entregas de mañana (un solo aviso con la cantidad)
        LocalDate tomorrow = today.plusDays(1);
        List<Order> tomorrowOrders = orderRepository.findByDeliveryDateBetweenAndStatusIn(tomorrow, tomorrow, ACTIVE);
        if (!tomorrowOrders.isEmpty()) {
            long unconfirmed = tomorrowOrders.stream().filter(o -> o.getStatus() == OrderStatus.PENDING).count();
            String text = tomorrowOrders.size() + (tomorrowOrders.size() == 1 ? " pedido" : " pedidos") + " para entregar"
                    + (unconfirmed > 0 ? " (" + unconfirmed + " sin confirmar)" : "") + ".";
            items.add(item("tomorrow:" + tomorrow + ":" + tomorrowOrders.size(), "TOMORROW", "Entregas de mañana",
                    text, "pedidos/agendados", false));
        }

        // 5 y 6. Insumos: stock en el mínimo o menos (misma regla que la pestaña Inventario) y vencimientos
        for (InventoryItem i : inventoryRepository.findAll()) {
            double stock = i.getCurrentStock() == null ? 0 : i.getCurrentStock();
            double min = i.getMinimumStock() == null ? 0 : i.getMinimumStock();
            String unit = i.getUnit() == null ? "" : " " + i.getUnit();
            if (stock <= min) {
                items.add(item("stock:" + i.getId() + ":" + stock, "STOCK", "Stock bajo: " + i.getName(),
                        "Quedan " + fmt(stock) + unit + " (mínimo " + fmt(min) + unit + ").", "inventario", false));
            }
            LocalDate exp = i.getExpirationDate();
            if (exp != null && !exp.isAfter(today.plusDays(EXPIRING_DAYS))) {
                long days = ChronoUnit.DAYS.between(today, exp);
                String text = days < 0 ? "Venció el " + exp.format(DAY) + "."
                        : days == 0 ? "Vence hoy." : days == 1 ? "Vence mañana." : "Vence en " + days + " días (" + exp.format(DAY) + ").";
                items.add(item("expiring:" + i.getId() + ":" + exp, "EXPIRING", (days < 0 ? "Vencido: " : "Por vencer: ") + i.getName(),
                        text, "inventario", false));
            }
        }

        // 7. Cumpleaños de clientes hoy (solo admin)
        if (admin) {
            for (Customer c : customerRepository.findByBirthDateIsNotNull()) {
                if (BirthdayService.isBirthdayOn(c.getBirthDate(), today)) {
                    items.add(item("birthday:" + c.getId() + ":" + today, "BIRTHDAY", "Cumpleaños de hoy",
                            name(c.getName()) + " cumple años hoy.", "clientes", false));
                }
            }
        }

        List<Item> shown = items.size() > MAX_ITEMS ? items.subList(0, MAX_ITEMS) : items;
        Set<String> read = shown.isEmpty() ? Set.of()
                : new HashSet<>(readRepository.findReadKeys(user, shown.stream().map(Item::key).toList()));
        List<Item> result = shown.stream()
                .map(it -> new Item(it.key(), it.type(), it.title(), it.text(), it.tab(), read.contains(it.key()), it.sound()))
                .toList();
        int unread = (int) result.stream().filter(it -> !it.read()).count();
        return new Result(unread, result);
    }

    /**
     * Marca avisos como leídos. Recibe las claves que la persona está viendo (para "marcar todas",
     * todas las de la lista): así un aviso que llegó recién no queda leído sin haberlo visto.
     * @return cuántas se marcaron (las que ya estaban leídas o tienen forma rara se saltean)
     */
    @Transactional
    public int markRead(User user, Collection<String> keys) {
        if (keys == null || keys.isEmpty()) return 0;
        List<String> valid = keys.stream().filter(Objects::nonNull).filter(k -> KEY.matcher(k).matches())
                .distinct().limit(100).toList();
        if (valid.isEmpty()) return 0;
        Set<String> already = new HashSet<>(readRepository.findReadKeys(user, valid));
        LocalDateTime now = LocalDateTime.now();
        int saved = 0;
        for (String k : valid) {
            if (already.contains(k)) continue;
            readRepository.save(new NotificationRead(user, k, now));
            saved++;
        }
        // Limpieza: marcas de hace más de 90 días (esos avisos ya no existen)
        readRepository.deleteOlderThan(now.minusDays(90));
        return saved;
    }

    private static Item item(String key, String type, String title, String text, String tab, boolean sound) {
        return new Item(key, type, title, text, tab, false, sound);
    }

    // Nombre corto para el aviso: los datos completos están en la pestaña
    private static String name(String n) {
        return n == null || n.isBlank() ? "Cliente" : n.trim();
    }

    // 2.0 → "2"; 1.5 → "1,5"
    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v).replace('.', ',');
    }
}
