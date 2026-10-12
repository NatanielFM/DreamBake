package com.dreambake.ai.tools;

import com.dreambake.audit.AuditText;
import com.dreambake.controller.InventoryRestController;
import com.dreambake.controller.OrderRestController;
import com.dreambake.entity.*;
import com.dreambake.repository.InventoryItemRepository;
import com.dreambake.repository.OrderRepository;
import com.dreambake.repository.ProductRepository;
import com.dreambake.repository.UserRepository;
import com.dreambake.service.ReminderService;
import com.dreambake.service.StockCalculator;
import com.dreambake.util.InputSanitizer;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.dreambake.ai.tools.BakeyTool.*;

/**
 * Herramientas de ACCIÓN de Bakey (paso 5.4): las que cambian datos.
 *
 * REGLA DE ORO: ninguna de estas herramientas cambia nada cuando el modelo la pide. Cada una:
 *  1. revisa los datos y los permisos (igual que el panel) y, si algo está mal, devuelve un error;
 *  2. arma una propuesta legible (ActionProposal) que queda pendiente en BakeyActionService;
 *  3. el cambio de verdad (el "ejecutor") corre recién cuando la persona toca "Confirmar".
 *
 * Para usar "la misma lógica y validaciones que el panel", los ejecutores llaman a los mismos métodos
 * de los controladores del panel (OrderRestController, InventoryRestController) y a ReminderService.
 * Así un pedido creado por Bakey pasa por exactamente el mismo código que uno cargado a mano.
 *
 * Permisos (los mismos del panel del pastelero, baker.html): el pastelero puede crear pedidos (sin seña
 * ni total), confirmar, marcar entregado y volver a agendado, y manejar sus recordatorios. No puede
 * rechazar ni cancelar pedidos ni tocar el stock (en su panel esos botones no están).
 */
@Component
public class BakeyActionTools {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEEE dd/MM 'a las' HH:mm", Locale.forLanguageTag("es-AR"));
    private static final DateTimeFormatter WEEKDAY = DateTimeFormatter.ofPattern("EEEE", Locale.forLanguageTag("es-AR"));

    private final OrderRestController orderController;
    private final InventoryRestController inventoryController;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final InventoryItemRepository inventoryRepository;
    private final UserRepository userRepository;
    private final ReminderService reminderService;
    private final StockCalculator stockCalculator;

    public BakeyActionTools(OrderRestController orderController, InventoryRestController inventoryController,
                            OrderRepository orderRepository, ProductRepository productRepository,
                            InventoryItemRepository inventoryRepository, UserRepository userRepository,
                            ReminderService reminderService, StockCalculator stockCalculator) {
        this.orderController = orderController;
        this.inventoryController = inventoryController;
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.userRepository = userRepository;
        this.reminderService = reminderService;
        this.stockCalculator = stockCalculator;
    }

    public List<BakeyTool> tools() {
        return List.of(
                BakeyTool.action("crear_pedido",
                        "Propone crear un pedido nuevo (queda en estado Nuevo). NO se crea hasta que la persona "
                                + "confirme. Pedí antes los datos que falten: cliente, teléfono, productos y fecha.",
                        params(param("cliente", "string", "Nombre del cliente"),
                                param("telefono", "string", "Teléfono del cliente"),
                                list("productos", "Productos del pedido",
                                        param("producto", "string", "Nombre del producto del catálogo, o 'Torta personalizada'"),
                                        optional("cantidad", "integer", "Cantidad (por defecto 1)"),
                                        optional("detalle", "string", "Detalles: sabores, porciones, dedicatoria…")),
                                param("fecha_entrega", "string", "Fecha de entrega AAAA-MM-DD"),
                                optional("turno", "string", "mañana o tarde"),
                                optional("metodo", "string", "retiro o envío (por defecto retiro)"),
                                optional("direccion", "string", "Dirección, obligatoria si es envío"),
                                optional("sena", "number", "Seña cobrada (solo la administradora)"),
                                optional("total", "number", "Total del pedido si no sale del catálogo, p. ej. tortas personalizadas (solo la administradora)")),
                        ALL_STAFF, this::proposeOrder),
                BakeyTool.action("cambiar_estado_pedido",
                        "Propone cambiar el estado de un pedido: agendado (confirmar), entregado, volver a agendado, "
                                + "cancelado o rechazado. NO se cambia hasta que la persona confirme.",
                        params(param("id", "integer", "Número del pedido"),
                                param("estado", "string", "agendado, entregado, cancelado o rechazado"),
                                optional("sena", "number", "Seña al confirmar (solo la administradora)")),
                        ALL_STAFF, this::proposeStatus),
                BakeyTool.action("ajustar_stock",
                        "Propone corregir el stock de un insumo (por ejemplo si se rompió o se tiró algo, o si al "
                                + "contar hay otra cantidad). NO se cambia hasta que la persona confirme.",
                        params(param("insumo", "string", "Nombre del insumo"),
                                param("cambio", "number", "Cuánto sumar (positivo) o restar (negativo)"),
                                param("motivo", "string", "Por qué se ajusta")),
                        ADMIN_ONLY, this::proposeAdjust),
                BakeyTool.action("registrar_compra_insumos",
                        "Propone sumar al stock los insumos que se compraron. NO se cambia hasta que la persona confirme.",
                        params(list("items", "Insumos comprados",
                                param("insumo", "string", "Nombre del insumo"),
                                param("cantidad", "number", "Cantidad comprada (en la unidad del insumo)"))),
                        ADMIN_ONLY, this::proposePurchase),
                BakeyTool.action("crear_recordatorio",
                        "Propone crear un recordatorio para la persona que está hablando con vos. NO se crea hasta que confirme.",
                        params(param("titulo", "string", "Qué hay que hacer"),
                                param("fecha_hora", "string", "Cuándo, formato AAAA-MM-DDTHH:mm"),
                                optional("notas", "string", "Detalle opcional"),
                                optional("pedido", "integer", "Número de pedido relacionado (opcional)")),
                        ALL_STAFF, this::proposeReminder),
                BakeyTool.action("marcar_recordatorio_hecho",
                        "Propone marcar como hecho un recordatorio (usá el id que da mis_recordatorios). NO se cambia hasta que confirme.",
                        params(param("id", "integer", "Id del recordatorio")),
                        ALL_STAFF, this::proposeReminderDone)
        );
    }

    // ---------------------------------------------------------------------
    // crear_pedido
    // ---------------------------------------------------------------------

    private record ItemSpec(Long productId, String name, int quantity, String detail, Double unitPrice) {}

    private ActionProposal proposeOrder(ToolContext ctx, ToolArgs args) {
        // Los textos vienen del modelo (que a su vez puede haber leído textos de clientes): se limpian igual
        // que los pedidos de la tienda, para que nunca se pueda meter código en el panel
        String customer = InputSanitizer.clean(args.text("cliente", 120), 120);
        String phone = InputSanitizer.clean(args.text("telefono", 40), 40);
        if (customer == null || customer.isBlank()) throw new ToolArgs.InvalidArgException("Falta el nombre del cliente.");
        if (phone == null || phone.replaceAll("\\D", "").length() < 6) {
            throw new ToolArgs.InvalidArgException("Falta el teléfono del cliente (al menos 6 números).");
        }
        LocalDate date = args.date("fecha_entrega");
        if (date.isBefore(ctx.today())) throw new ToolArgs.InvalidArgException("La fecha de entrega ya pasó.");
        if (date.isAfter(ctx.today().plusYears(1))) throw new ToolArgs.InvalidArgException("La fecha de entrega está a más de un año.");
        String shift = normalizeShift(args.text("turno", 60));
        String method = normalize(args.text("metodo", 20)).startsWith("env") ? "Envío" : "Retiro";
        String address = InputSanitizer.clean(args.text("direccion", 250), 250);
        if ("Envío".equals(method) && (address == null || address.isBlank())) {
            throw new ToolArgs.InvalidArgException("Para un envío hace falta la dirección.");
        }

        // Productos: los del catálogo se buscan por nombre; lo demás es una torta personalizada / a pedido
        List<ItemSpec> specs = new ArrayList<>();
        int units = 0;
        for (ToolArgs item : args.list("productos", 20)) {
            String wanted = item.text("producto", 120);
            if (wanted.isBlank()) throw new ToolArgs.InvalidArgException("Hay un producto sin nombre.");
            int qty = item.integer("cantidad", 1, 1, 20);
            String detail = InputSanitizer.clean(item.text("detalle", 500), 500);
            Product product = findProduct(wanted);
            if (product != null) {
                specs.add(new ItemSpec(product.getId(), product.getName(), qty, blankToNull(detail), product.getPrice()));
            } else {
                specs.add(new ItemSpec(null, InputSanitizer.clean(wanted, 120), qty, blankToNull(detail), null));
            }
            units += qty;
        }
        if (units > 50) throw new ToolArgs.InvalidArgException("Son demasiadas unidades para un pedido (máximo 50).");

        // Plata: solo la administradora (el panel del pastelero tampoco tiene total ni seña)
        Double totalArg = args.decimal("total");
        Double deposit = args.decimal("sena");
        if (!ctx.admin() && (totalArg != null || deposit != null)) {
            throw new ToolArgs.InvalidArgException("El pastelero no carga montos: la seña y el total los carga la administración desde el panel.");
        }
        double catalogTotal = specs.stream().filter(s -> s.unitPrice() != null).mapToDouble(s -> s.unitPrice() * s.quantity()).sum();
        double total = totalArg != null ? totalArg : catalogTotal;
        if (total < 0 || (deposit != null && deposit < 0)) throw new ToolArgs.InvalidArgException("Los montos no pueden ser negativos.");
        if (deposit != null && deposit > total) throw new ToolArgs.InvalidArgException("La seña no puede ser mayor que el total.");
        boolean hasCustom = specs.stream().anyMatch(s -> s.productId() == null);

        // Resumen legible para la tarjeta
        List<String> lines = new ArrayList<>();
        lines.add("Cliente: " + customer + " · Tel. " + phone);
        lines.add("Entrega: " + date.format(WEEKDAY) + " " + date.format(DATE) + (shift != null ? ", " + shift : "")
                + ", " + method + ("Envío".equals(method) ? " a " + address : ""));
        for (ItemSpec s : specs) {
            lines.add("• " + s.quantity() + " x " + s.name()
                    + (s.productId() == null ? " (a pedido)" : "")
                    + (s.detail() != null ? " — " + s.detail() : ""));
        }
        if (ctx.admin()) {
            lines.add("Total: " + AuditText.money(total) + (deposit != null ? " · Seña: " + AuditText.money(deposit) : " · Sin seña"));
            if (hasCustom && totalArg == null) lines.add("Ojo: lo que es a pedido queda sin precio; cargalo en el panel.");
        }
        lines.add("Estado: Nuevo (después se confirma como siempre)");

        final double finalTotal = total;
        return new ActionProposal("Crear pedido", lines, () -> {
            Order order = new Order();
            order.setCustomerName(customer);
            order.setCustomerPhone(phone);
            order.setDeliveryDate(date);
            order.setDeliveryShift(shift);
            order.setDeliveryMethod(method);
            order.setDeliveryAddress(address);
            order.setTotalPrice(finalTotal);
            order.setDeposit(deposit == null ? 0.0 : deposit);
            order.setStatus(OrderStatus.PENDING);   // siempre queda Nuevo
            List<OrderItem> items = new ArrayList<>();
            for (ItemSpec s : specs) {
                Product product = null;
                if (s.productId() != null) {
                    product = productRepository.findById(s.productId()).filter(Product::isEnabled).orElseThrow(() ->
                            new ActionProposal.ActionFailedException("El producto «" + s.name() + "» ya no está en el catálogo."));
                }
                for (int i = 0; i < s.quantity(); i++) {   // un ítem por unidad, como el panel y la tienda
                    OrderItem item = new OrderItem();
                    item.setProduct(product);
                    item.setProductType(s.name());
                    item.setIsCustomCake(product == null);
                    item.setDetails(s.detail());
                    item.setPrice(s.unitPrice() == null ? 0.0 : s.unitPrice());
                    items.add(item);
                }
            }
            order.setItems(items);
            // El mismo método que usa el botón "Nuevo pedido" del panel
            Order saved = (Order) okBody(orderController.createOrder(order, currentAuth()));
            return "Listo: se creó el pedido #" + saved.getId() + " de " + customer + " para el " + date.format(DATE) + " (estado Nuevo).";
        }, null);
    }

    // ---------------------------------------------------------------------
    // cambiar_estado_pedido
    // ---------------------------------------------------------------------

    private ActionProposal proposeStatus(ToolContext ctx, ToolArgs args) {
        long id = args.id("id");
        Order order = orderRepository.findById(id).filter(o -> o.getStatus() != OrderStatus.REJECTED)
                .orElseThrow(() -> new ToolArgs.InvalidArgException("No existe el pedido N° " + id + "."));
        OrderStatus from = order.getStatus();
        OrderStatus to = parseTarget(args.text("estado", 30));
        if (from == to) throw new ToolArgs.InvalidArgException("El pedido ya está en «" + AuditText.status(to) + "».");
        if (!allowedTransition(from, to, ctx.admin())) {
            boolean adminCould = allowedTransition(from, to, true);
            throw new ToolArgs.InvalidArgException(adminCould && !ctx.admin()
                    ? "Eso no se puede hacer desde el panel del pastelero: pedíselo a la administración."
                    : "Un pedido «" + AuditText.status(from) + "» no puede pasar a «" + AuditText.status(to) + "».");
        }
        boolean confirming = from == OrderStatus.PENDING && to == OrderStatus.CONFIRMED;
        Double deposit = args.decimal("sena");
        if (deposit != null && (!ctx.admin() || !confirming)) {
            throw new ToolArgs.InvalidArgException(ctx.admin() ? "La seña solo se carga al confirmar un pedido nuevo."
                    : "El pastelero no carga señas: la carga la administración desde el panel.");
        }
        double total = order.getTotalPrice() == null ? 0 : order.getTotalPrice();
        if (deposit != null && (deposit < 0 || deposit > total)) {
            throw new ToolArgs.InvalidArgException("La seña tiene que estar entre 0 y el total del pedido (" + AuditText.money(total) + ").");
        }
        // Confirmar descuenta insumos: si falta stock se avisa ya. Forzar la confirmación queda solo para el panel.
        if (confirming) {
            List<String> missing = missingSupplies(order);
            if (!missing.isEmpty()) {
                throw new ToolArgs.InvalidArgException("No alcanza el stock para confirmarlo (" + String.join(", ", missing)
                        + "). Si querés confirmarlo igual, hacelo desde el panel con «Forzar».");
            }
        }

        List<String> lines = new ArrayList<>();
        lines.add("Pedido #" + order.getId() + " de " + order.getCustomerName()
                + (order.getDeliveryDate() != null ? " (entrega " + order.getDeliveryDate().format(DATE) + ")" : ""));
        lines.add("Estado: " + AuditText.status(from) + " → " + AuditText.status(to));
        if (confirming) {
            if (ctx.admin()) lines.add("Seña: " + AuditText.money(deposit == null ? 0.0 : deposit));
            lines.add("Se descuentan del stock los insumos de las recetas.");
        }

        return new ActionProposal("Cambiar estado del pedido", lines, () -> {
            // Si el pedido cambió desde que se propuso, no se toca (la tarjeta ya no dice la verdad)
            Order current = orderRepository.findById(id).orElseThrow(() ->
                    new ActionProposal.ActionFailedException("El pedido #" + id + " ya no existe."));
            if (current.getStatus() != from) {
                throw new ActionProposal.ActionFailedException("El pedido #" + id + " cambió mientras tanto (ahora está «"
                        + AuditText.status(current.getStatus()) + "»). Pedímelo de nuevo.");
            }
            // Los mismos métodos que usan los botones del panel (Confirmar / Entregado / Cancelar…)
            if (confirming) {
                okBody(orderController.confirmOrder(id, new OrderRestController.ConfirmRequest(
                        deposit == null ? 0.0 : deposit, null, false)));
            } else {
                okBody(orderController.updateStatus(id, new OrderRestController.StatusRequest(to, false)));
            }
            return "Listo: el pedido #" + id + " pasó a «" + AuditText.status(to) + "».";
        }, null);
    }

    // Transiciones que permite el panel (orders.js): el pastelero no ve Rechazar ni Cancelar
    private boolean allowedTransition(OrderStatus from, OrderStatus to, boolean admin) {
        if (from == OrderStatus.PENDING && to == OrderStatus.CONFIRMED) return true;                 // Confirmar
        if ((from == OrderStatus.CONFIRMED || from == OrderStatus.IN_PROGRESS) && to == OrderStatus.COMPLETED) return true; // Entregado
        if (from == OrderStatus.COMPLETED && to == OrderStatus.CONFIRMED) return true;               // Volver a agendado
        if (!admin) return false;
        if (from == OrderStatus.PENDING && to == OrderStatus.REJECTED) return true;                  // Rechazar
        return to == OrderStatus.CANCELLED
                && (from == OrderStatus.PENDING || from == OrderStatus.CONFIRMED || from == OrderStatus.IN_PROGRESS); // Cancelar
    }

    private OrderStatus parseTarget(String text) {
        String t = normalize(text);
        if (t.contains("agend") || t.contains("confirm")) return OrderStatus.CONFIRMED;
        if (t.startsWith("entreg") || t.startsWith("complet") || t.startsWith("termin")) return OrderStatus.COMPLETED;
        if (t.startsWith("cancel")) return OrderStatus.CANCELLED;
        if (t.startsWith("rechaz")) return OrderStatus.REJECTED;
        throw new ToolArgs.InvalidArgException("Estado desconocido: usá agendado, entregado, cancelado o rechazado.");
    }

    // Insumos que no alcanzan para el pedido (misma regla que el panel: vencido = 0)
    private List<String> missingSupplies(Order order) {
        List<String> missing = new ArrayList<>();
        for (Map.Entry<Long, Double> need : stockCalculator.requiredFor(order).entrySet()) {
            InventoryItem item = inventoryRepository.findById(need.getKey()).orElse(null);
            if (item == null) { missing.add("un insumo que ya no existe"); continue; }
            if (stockCalculator.usableStock(item) < need.getValue()) missing.add(item.getName());
        }
        return missing;
    }

    // ---------------------------------------------------------------------
    // ajustar_stock y registrar_compra_insumos (solo ADMIN)
    // ---------------------------------------------------------------------

    private ActionProposal proposeAdjust(ToolContext ctx, ToolArgs args) {
        InventoryItem item = findSupply(args.text("insumo", 80));
        Double change = args.decimal("cambio");
        if (change == null || change == 0) throw new ToolArgs.InvalidArgException("Falta cuánto sumar o restar (distinto de 0).");
        if (Math.abs(change) > 100000) throw new ToolArgs.InvalidArgException("El cambio es demasiado grande.");
        String reason = InputSanitizer.clean(args.text("motivo", 150), 150);
        if (reason == null || reason.isBlank()) throw new ToolArgs.InvalidArgException("Falta el motivo del ajuste.");
        double before = item.getCurrentStock() == null ? 0 : item.getCurrentStock();
        if (before + change < 0) {
            throw new ToolArgs.InvalidArgException("No puede quedar negativo: hay " + AuditText.number(before) + " " + item.getUnit() + ".");
        }
        List<String> lines = List.of(
                "Insumo: " + item.getName(),
                "Stock: " + AuditText.number(before) + " → " + AuditText.number(before + change) + " " + item.getUnit()
                        + " (" + (change > 0 ? "+" : "") + AuditText.number(change) + ")",
                "Motivo: " + reason);
        Long id = item.getId();
        return new ActionProposal("Ajustar stock", lines, () -> {
            InventoryItem updated = applyStockChange(id, change);
            return "Listo: «" + updated.getName() + "» quedó en " + AuditText.number(updated.getCurrentStock()) + " " + updated.getUnit() + ".";
        }, "motivo: " + reason);
    }

    private ActionProposal proposePurchase(ToolContext ctx, ToolArgs args) {
        // Se juntan por insumo (si el modelo repite uno, se suma)
        Map<Long, Double> amounts = new LinkedHashMap<>();
        Map<Long, InventoryItem> items = new HashMap<>();
        for (ToolArgs entry : args.list("items", 20)) {
            InventoryItem item = findSupply(entry.text("insumo", 80));
            Double qty = entry.decimal("cantidad");
            if (qty == null || qty <= 0 || qty > 100000) {
                throw new ToolArgs.InvalidArgException("La cantidad de «" + item.getName() + "» tiene que ser mayor que 0.");
            }
            amounts.merge(item.getId(), qty, Double::sum);
            items.put(item.getId(), item);
        }
        List<String> lines = new ArrayList<>();
        amounts.forEach((id, qty) -> {
            InventoryItem item = items.get(id);
            double before = item.getCurrentStock() == null ? 0 : item.getCurrentStock();
            lines.add(item.getName() + ": " + AuditText.number(before) + " → " + AuditText.number(before + qty) + " "
                    + item.getUnit() + " (+" + AuditText.number(qty) + ")");
        });
        return new ActionProposal("Registrar compra de insumos", lines, () -> {
            // Todo dentro de la misma transacción (BakeyActionService): si uno falla, no se suma ninguno
            amounts.forEach(this::applyStockChange);
            return "Listo: se sumó al stock la compra de " + amounts.size() + (amounts.size() == 1 ? " insumo." : " insumos.");
        }, "compra de insumos");
    }

    // Suma o resta sobre el stock ACTUAL (por si cambió desde que se propuso) usando el mismo método
    // del panel (InventoryRestController.updateItem), que también lo registra en la auditoría
    private InventoryItem applyStockChange(Long id, double change) {
        InventoryItem item = inventoryRepository.findById(id).orElseThrow(() ->
                new ActionProposal.ActionFailedException("Ese insumo ya no existe."));
        double now = item.getCurrentStock() == null ? 0 : item.getCurrentStock();
        double newStock = Math.round((now + change) * 1000.0) / 1000.0;
        if (newStock < 0) {
            throw new ActionProposal.ActionFailedException("«" + item.getName() + "» quedaría negativo (ahora hay "
                    + AuditText.number(now) + " " + item.getUnit() + ").");
        }
        return (InventoryItem) okBody(inventoryController.updateItem(id, new InventoryRestController.InventoryRequest(
                item.getName(), newStock, item.getMinimumStock(), item.getUnit(), item.getExpirationDate(), item.getUnitCost())));
    }

    // ---------------------------------------------------------------------
    // Recordatorios
    // ---------------------------------------------------------------------

    private ActionProposal proposeReminder(ToolContext ctx, ToolArgs args) {
        LocalDateTime when = args.dateTime("fecha_hora");
        ReminderService.ValidReminder valid;
        try {
            valid = reminderService.validate(args.text("titulo", 120), args.text("notas", 1000), when, args.optionalId("pedido"));
        } catch (ReminderService.InvalidReminderException e) {
            throw new ToolArgs.InvalidArgException(e.getMessage());
        }
        List<String> lines = new ArrayList<>();
        lines.add("Recordatorio: " + valid.title());
        lines.add("Cuándo: " + valid.dueAt().format(WHEN));
        if (valid.notes() != null) lines.add("Notas: " + valid.notes());
        if (valid.relatedOrderId() != null) lines.add("Pedido #" + valid.relatedOrderId());
        Long userId = ctx.user().getId();
        return new ActionProposal("Crear recordatorio", lines, () -> {
            User owner = userRepository.findById(userId).orElseThrow(() ->
                    new ActionProposal.ActionFailedException("Tu usuario ya no existe."));
            try {
                reminderService.create(owner, valid.title(), valid.notes(), valid.dueAt(), valid.relatedOrderId(), true);
            } catch (ReminderService.InvalidReminderException e) {
                throw new ActionProposal.ActionFailedException(e.getMessage());   // por ejemplo, la hora ya pasó
            }
            return "Listo: te lo recuerdo el " + valid.dueAt().format(WHEN) + ".";
        }, null);
    }

    private ActionProposal proposeReminderDone(ToolContext ctx, ToolArgs args) {
        long id = args.id("id");
        Reminder reminder;
        try {
            reminder = reminderService.findAllowed(id, ctx.user(), ctx.admin());
        } catch (ReminderService.ReminderNotFoundException e) {
            throw new ToolArgs.InvalidArgException("No encontré el recordatorio N° " + id + ".");
        }
        if (reminder.isDone()) throw new ToolArgs.InvalidArgException("Ese recordatorio ya está hecho.");
        List<String> lines = List.of("Recordatorio: " + reminder.getTitle() + " (" + reminder.getDueAt().format(WHEN) + ")",
                "Pasa a: Hecho");
        Long userId = ctx.user().getId();
        boolean admin = ctx.admin();
        return new ActionProposal("Marcar recordatorio como hecho", lines, () -> {
            User user = userRepository.findById(userId).orElseThrow(() ->
                    new ActionProposal.ActionFailedException("Tu usuario ya no existe."));
            try {
                reminderService.setDone(id, true, user, admin);
            } catch (ReminderService.ReminderNotFoundException e) {
                throw new ActionProposal.ActionFailedException("Ese recordatorio ya no existe.");
            }
            return "Listo: el recordatorio quedó hecho.";
        }, null);
    }

    // ---------------------------------------------------------------------
    // Ayudas
    // ---------------------------------------------------------------------

    // Producto habilitado del catálogo por nombre (exacto o, si no, que lo contenga). null = no está.
    private Product findProduct(String text) {
        String wanted = normalize(text);
        List<Product> enabled = productRepository.findAll().stream().filter(Product::isEnabled).toList();
        List<Product> exact = enabled.stream().filter(p -> normalize(p.getName()).equals(wanted)).toList();
        if (exact.size() == 1) return exact.get(0);
        List<Product> similar = enabled.stream().filter(p -> {
            String name = normalize(p.getName());
            return name.contains(wanted) || (wanted.contains(name) && name.length() >= 4);
        }).toList();
        if (similar.size() == 1) return similar.get(0);
        if (similar.size() > 1) {
            throw new ToolArgs.InvalidArgException("Hay varios productos parecidos a «" + text + "»: "
                    + String.join(", ", similar.stream().map(Product::getName).toList()) + ". ¿Cuál es?");
        }
        return null;
    }

    // Insumo por nombre (exacto o único parecido); si no, error con los nombres disponibles
    private InventoryItem findSupply(String text) {
        String wanted = normalize(text);
        if (wanted.isBlank()) throw new ToolArgs.InvalidArgException("Falta el nombre del insumo.");
        List<InventoryItem> all = inventoryRepository.findAll();
        List<InventoryItem> exact = all.stream().filter(i -> normalize(i.getName()).equals(wanted)).toList();
        if (exact.size() == 1) return exact.get(0);
        List<InventoryItem> similar = all.stream().filter(i -> normalize(i.getName()).contains(wanted)).toList();
        if (similar.size() == 1) return similar.get(0);
        if (similar.size() > 1) {
            throw new ToolArgs.InvalidArgException("Hay varios insumos parecidos a «" + text + "»: "
                    + String.join(", ", similar.stream().map(InventoryItem::getName).toList()) + ". ¿Cuál es?");
        }
        throw new ToolArgs.InvalidArgException("No encontré el insumo «" + text + "». Los insumos cargados son: "
                + String.join(", ", all.stream().map(InventoryItem::getName).limit(20).toList()) + ".");
    }

    // "mañana" → "Por la mañana", "tarde" → "Por la tarde" (como el panel); otro texto se deja limpio
    private String normalizeShift(String text) {
        String t = normalize(text);
        if (t.isBlank()) return null;
        if (t.contains("manana")) return "Por la mañana";
        if (t.contains("tarde")) return "Por la tarde";
        return InputSanitizer.clean(text, 60);
    }

    // Respuesta del controlador del panel: si no fue 2xx, el mensaje de error pasa a la persona
    private static Object okBody(ResponseEntity<?> response) {
        if (response.getStatusCode().is2xxSuccessful()) return response.getBody();
        Object body = response.getBody();
        String message = body instanceof Map<?, ?> map && map.get("message") != null
                ? String.valueOf(map.get("message")) : "El panel rechazó el cambio (código " + response.getStatusCode().value() + ").";
        throw new ActionProposal.ActionFailedException(message);
    }

    // Quién confirmó (la acción se ejecuta dentro de su petición de "Confirmar")
    private static Authentication currentAuth() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private static String normalize(String text) {
        return BakeyQueryTools.normalize(text);
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text;
    }
}
