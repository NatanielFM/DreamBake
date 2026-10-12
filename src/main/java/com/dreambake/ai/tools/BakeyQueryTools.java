package com.dreambake.ai.tools;

import com.dreambake.audit.AuditText;
import com.dreambake.entity.*;
import com.dreambake.repository.*;
import com.dreambake.service.BirthdayService;
import com.dreambake.service.ReminderService;
import com.dreambake.service.StockCalculator;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static com.dreambake.ai.tools.BakeyTool.*;

/**
 * Herramientas de SOLO LECTURA de Bakey (paso 5.3). Cada una es un método que usa los repositorios
 * y servicios que ya existen y devuelve un resultado chico (un Map que se convierte en JSON).
 *
 * Reglas que siguen todas:
 *  - Nunca cambian datos (solo consultan). Las que cambian datos llegan en el paso 5.4.
 *  - Devuelven lo mínimo: nombres de clientes sí, contacto tapado (ver BakeyPrivacy), sin plata para el pastelero.
 *  - Listas con tope (MAX_ITEMS) y "hayMas" si se cortaron, para no mandar al modelo miles de filas.
 *  - Claves en español y fechas dd/MM/yyyy, para que el modelo las lea y las repita fácil.
 */
@Component
public class BakeyQueryTools {

    static final int MAX_ITEMS = 40;
    static final int MAX_RANGE_DAYS = 93;   // unos 3 meses
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter WEEKDAY = DateTimeFormatter.ofPattern("EEEE", Locale.forLanguageTag("es-AR"));

    // Estados que cuentan como venta (los mismos que usa la ficha de clientes, CustomerRestController)
    private static final List<OrderStatus> SOLD = List.of(OrderStatus.CONFIRMED, OrderStatus.IN_PROGRESS, OrderStatus.COMPLETED);
    // Pedidos "vivos" (todavía no entregados ni cancelados)
    private static final List<OrderStatus> ACTIVE = List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.IN_PROGRESS);
    // Todos menos "Rechazado" (los rechazados no se muestran en el panel)
    private static final List<OrderStatus> ALL_VISIBLE = List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED,
            OrderStatus.IN_PROGRESS, OrderStatus.COMPLETED, OrderStatus.CANCELLED);

    private static final String CONTACT_PARAM_DESC =
            "Poné true SOLO si la persona pidió explícitamente un teléfono, email o dirección. Si no, no lo mandes.";

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final InventoryItemRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final CakeSizeRepository cakeSizeRepository;
    private final BirthdayService birthdayService;
    private final ReminderService reminderService;
    private final StockCalculator stockCalculator;

    public BakeyQueryTools(OrderRepository orderRepository, CustomerRepository customerRepository,
                           InventoryItemRepository inventoryRepository, ProductRepository productRepository,
                           CakeSizeRepository cakeSizeRepository, BirthdayService birthdayService,
                           ReminderService reminderService, StockCalculator stockCalculator) {
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.inventoryRepository = inventoryRepository;
        this.productRepository = productRepository;
        this.cakeSizeRepository = cakeSizeRepository;
        this.birthdayService = birthdayService;
        this.reminderService = reminderService;
        this.stockCalculator = stockCalculator;
    }

    /** La lista de herramientas: nombre, descripción para el modelo, parámetros, roles y código. */
    public List<BakeyTool> tools() {
        return List.of(
                new BakeyTool("pedidos_por_fecha",
                        "Lista los pedidos cuya fecha de ENTREGA está entre dos fechas (incluidas). Sirve para "
                                + "'¿qué entregas tengo el viernes?' (desde y hasta = ese día) o 'pedidos de la semana'.",
                        params(param("desde", "string", "Primera fecha, formato AAAA-MM-DD"),
                                param("hasta", "string", "Última fecha, formato AAAA-MM-DD"),
                                optional("estado", "string", "Opcional: nuevo, agendado, en preparación, entregado o cancelado. Sin estado = todos.")),
                        ALL_STAFF, this::ordersByDate),
                new BakeyTool("pedidos_proximos",
                        "Pedidos para entregar desde hoy hasta dentro de N días que todavía no se entregaron "
                                + "(nuevos, agendados y en preparación).",
                        params(optional("dias", "integer", "Cuántos días hacia adelante (1 a 60). Por defecto 7.")),
                        ALL_STAFF, this::upcomingOrders),
                new BakeyTool("detalle_pedido",
                        "Todos los datos de un pedido por su número: productos con sus detalles (sabores, "
                                + "dedicatoria…), entrega, turno, si es retiro o envío y estado.",
                        params(param("id", "integer", "Número del pedido"),
                                optional("incluir_contacto", "boolean", CONTACT_PARAM_DESC)),
                        ALL_STAFF, this::orderDetail),
                new BakeyTool("buscar_cliente",
                        "Busca clientes por nombre o teléfono. Devuelve cuántos pedidos hizo, cuánto gastó, "
                                + "su último pedido y su cumpleaños (día y mes).",
                        params(param("texto", "string", "Nombre (o parte) o número de teléfono"),
                                optional("incluir_contacto", "boolean", CONTACT_PARAM_DESC)),
                        ADMIN_ONLY, this::searchCustomer),
                new BakeyTool("cumpleanos_proximos",
                        "Clientes que cumplen años desde hoy hasta dentro de N días.",
                        params(optional("dias", "integer", "Cuántos días hacia adelante (1 a 60). Por defecto 30.")),
                        ADMIN_ONLY, this::upcomingBirthdays),
                new BakeyTool("consultar_stock",
                        "Stock de los insumos del inventario. Buscá por nombre (ej: 'harina') o escribí 'bajo' "
                                + "para ver los que están en el mínimo o por debajo. Vacío = todos.",
                        params(optional("texto", "string", "Nombre del insumo, o 'bajo'")),
                        ALL_STAFF, this::stock),
                new BakeyTool("insumos_por_vencer",
                        "Insumos que vencen desde hoy hasta dentro de N días, y los que ya están vencidos.",
                        params(optional("dias", "integer", "Cuántos días hacia adelante (1 a 60). Por defecto 7.")),
                        ALL_STAFF, this::expiringSupplies),
                new BakeyTool("insumos_para_pedidos",
                        "Calcula, según las recetas, cuánto de cada insumo hace falta para los pedidos con entrega "
                                + "entre dos fechas y cuánto falta comprar. Sirve para '¿qué tengo que comprar para el finde?'.",
                        params(param("desde", "string", "Primera fecha de entrega, AAAA-MM-DD"),
                                param("hasta", "string", "Última fecha de entrega, AAAA-MM-DD")),
                        ALL_STAFF, this::suppliesForOrders),
                new BakeyTool("resumen_ventas",
                        "Resumen de ventas de los pedidos con entrega entre dos fechas: cantidad, facturado, "
                                + "cobrado, falta cobrar, ticket promedio, productos más pedidos y ganancia estimada.",
                        params(param("desde", "string", "Primera fecha, AAAA-MM-DD"),
                                param("hasta", "string", "Última fecha, AAAA-MM-DD")),
                        ADMIN_ONLY, this::salesSummary),
                new BakeyTool("productos_y_precios",
                        "Lista de productos que se venden en la tienda con su precio, y los tamaños de torta "
                                + "personalizada con su precio.",
                        params(),
                        ALL_STAFF, this::productsAndPrices),
                new BakeyTool("mis_recordatorios",
                        "Los recordatorios pendientes de la persona que está hablando con vos (incluye los vencidos).",
                        params(),
                        ALL_STAFF, this::myReminders)
        );
    }

    // ---------------------------------------------------------------------
    // Pedidos
    // ---------------------------------------------------------------------

    private Map<String, Object> ordersByDate(ToolContext ctx, ToolArgs args) {
        LocalDate from = args.date("desde");
        LocalDate to = args.date("hasta");
        ToolArgs.checkRange(from, to, MAX_RANGE_DAYS);
        String statusText = args.text("estado", 30);
        List<OrderStatus> statuses = statusText.isBlank() ? ALL_VISIBLE : List.of(parseStatus(statusText));

        List<Order> orders = sortedByDelivery(orderRepository.findByDeliveryDateBetweenAndStatusIn(from, to, statuses));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("desde", from.format(DATE));
        result.put("hasta", to.format(DATE));
        if (!statusText.isBlank()) result.put("estado", AuditText.status(statuses.get(0)));
        putOrderList(result, orders, ctx);
        return result;
    }

    private Map<String, Object> upcomingOrders(ToolContext ctx, ToolArgs args) {
        int days = args.integer("dias", 7, 1, 60);
        LocalDate to = ctx.today().plusDays(days);
        List<Order> orders = sortedByDelivery(orderRepository.findByDeliveryDateBetweenAndStatusIn(ctx.today(), to, ACTIVE));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("desde", ctx.today().format(DATE));
        result.put("hasta", to.format(DATE));
        putOrderList(result, orders, ctx);
        return result;
    }

    private Map<String, Object> orderDetail(ToolContext ctx, ToolArgs args) {
        long id = args.id("id");
        Order o = orderRepository.findById(id).orElse(null);
        if (o == null || o.getStatus() == OrderStatus.REJECTED) {
            return Map.of("error", "No existe el pedido N° " + id + ".");
        }
        boolean contact = BakeyPrivacy.contactAllowed(ctx, args);
        Map<String, Object> m = orderSummary(o, ctx);
        m.remove("productos");
        List<Map<String, Object>> items = new ArrayList<>();
        if (o.getItems() != null) {
            for (OrderItem item : o.getItems()) {
                Map<String, Object> i = new LinkedHashMap<>();
                i.put("producto", BakeyPrivacy.untrusted(itemName(item)));
                if (item.getDetails() != null && !item.getDetails().isBlank()) i.put("detalle", BakeyPrivacy.untrusted(cut(item.getDetails(), 400)));
                if (ctx.admin() && item.getPrice() != null) i.put("precio", item.getPrice());
                items.add(i);
            }
        }
        m.put("productos", items);
        // Contacto: tapado salvo que la persona lo haya pedido (BakeyPrivacy)
        putIfNotNull(m, "telefono", BakeyPrivacy.phone(o.getCustomerPhone(), contact));
        putIfNotNull(m, "email", BakeyPrivacy.email(o.getCustomerEmail(), contact));
        if ("Envío".equals(o.getDeliveryMethod())) putIfNotNull(m, "direccion", BakeyPrivacy.address(o.getDeliveryAddress(), contact));
        if (o.getCouponCode() != null && !o.getCouponCode().isBlank()) m.put("cupon", o.getCouponCode());
        if (o.getCreatedAt() != null) m.put("pedidoHechoEl", o.getCreatedAt().format(DATE_TIME));
        if (!contact) m.put("contactoTapado", true);
        m.put("aviso", BakeyPrivacy.UNTRUSTED_NOTICE);
        return m;
    }

    // Resumen de un pedido para las listas
    private Map<String, Object> orderSummary(Order o, ToolContext ctx) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pedido", o.getId());
        if (o.getDeliveryDate() != null) {
            m.put("entrega", o.getDeliveryDate().format(DATE) + " (" + o.getDeliveryDate().format(WEEKDAY) + ")");
        }
        putIfNotNull(m, "turno", BakeyPrivacy.untrusted(o.getDeliveryShift()));
        putIfNotNull(m, "metodo", o.getDeliveryMethod());
        m.put("estado", AuditText.status(o.getStatus()));
        m.put("cliente", BakeyPrivacy.untrusted(o.getCustomerName()));
        m.put("productos", BakeyPrivacy.untrusted(productsText(o)));
        // Plata: solo para el administrador (el panel del pastelero tampoco la muestra)
        if (ctx.admin()) {
            double total = o.getTotalPrice() == null ? 0 : o.getTotalPrice();
            double deposit = o.getDeposit() == null ? 0 : o.getDeposit();
            m.put("total", total);
            m.put("sena", deposit);
            m.put("saldo", o.getStatus() == OrderStatus.COMPLETED ? 0 : Math.max(0, total - deposit));
        }
        return m;
    }

    private void putOrderList(Map<String, Object> result, List<Order> orders, ToolContext ctx) {
        result.put("aviso", BakeyPrivacy.UNTRUSTED_NOTICE);
        result.put("cantidad", orders.size());
        result.put("pedidos", orders.stream().limit(MAX_ITEMS).map(o -> orderSummary(o, ctx)).toList());
        if (orders.size() > MAX_ITEMS) result.put("hayMas", orders.size() - MAX_ITEMS);
    }

    // "2 x Alfajores, Torta Personalizada" (un OrderItem por unidad: se agrupan)
    private String productsText(Order o) {
        if (o.getItems() == null || o.getItems().isEmpty()) return "";
        Map<String, Long> counts = o.getItems().stream()
                .collect(Collectors.groupingBy(this::itemName, LinkedHashMap::new, Collectors.counting()));
        return counts.entrySet().stream()
                .map(e -> e.getValue() > 1 ? e.getValue() + " x " + e.getKey() : e.getKey())
                .collect(Collectors.joining(", "));
    }

    private String itemName(OrderItem item) {
        if (item.getProduct() != null && item.getProduct().getName() != null) return item.getProduct().getName();
        return item.getProductType() == null ? "Producto" : item.getProductType();
    }

    private List<Order> sortedByDelivery(List<Order> orders) {
        List<Order> sorted = new ArrayList<>(orders);
        sorted.sort(Comparator.comparing(Order::getDeliveryDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Order::getId));
        return sorted;
    }

    // Estado en palabras del panel → OrderStatus
    private OrderStatus parseStatus(String text) {
        String t = normalize(text);
        if (t.startsWith("nuev") || t.startsWith("pendiente") || t.contains("sin confirmar")) return OrderStatus.PENDING;
        if (t.startsWith("agendad") || t.startsWith("confirmad")) return OrderStatus.CONFIRMED;
        if (t.contains("prepar")) return OrderStatus.IN_PROGRESS;
        if (t.startsWith("entregad") || t.startsWith("completad") || t.startsWith("terminad")) return OrderStatus.COMPLETED;
        if (t.startsWith("cancelad")) return OrderStatus.CANCELLED;
        throw new ToolArgs.InvalidArgException("Estado desconocido: usá nuevo, agendado, en preparación, entregado o cancelado.");
    }

    // ---------------------------------------------------------------------
    // Clientes (solo ADMIN)
    // ---------------------------------------------------------------------

    private Map<String, Object> searchCustomer(ToolContext ctx, ToolArgs args) {
        String text = args.text("texto", 60);
        if (text.length() < 2) return Map.of("error", "Escribí al menos 2 letras del nombre o números del teléfono.");
        boolean contact = BakeyPrivacy.contactAllowed(ctx, args);
        String needle = normalize(text);
        String digits = text.replaceAll("\\D", "");

        List<Customer> found = customerRepository.findAll().stream()
                .filter(c -> (c.getName() != null && normalize(c.getName()).contains(needle))
                        || (digits.length() >= 4 && c.getPhone() != null && c.getPhone().replaceAll("\\D", "").contains(digits)))
                .sorted(Comparator.comparing(Customer::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<Map<String, Object>> list = new ArrayList<>();
        for (Customer c : found.stream().limit(10).toList()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("cliente", BakeyPrivacy.untrusted(c.getName()));
            if (c.getTag() != null) m.put("etiqueta", c.getTag().getName());
            // Pedidos de la clienta: los unidos a su ficha y los viejos con su mismo teléfono
            String phone = c.getPhone() == null || c.getPhone().isBlank() ? null : c.getPhone().trim();
            List<Order> orders = orderRepository.findByCustomerOrCustomerPhoneOrderByDeliveryDateDesc(c, phone);
            List<Order> bought = orders.stream().filter(o -> SOLD.contains(o.getStatus())).toList();
            m.put("pedidosComprados", bought.size());
            m.put("totalGastado", bought.stream().mapToDouble(o -> o.getTotalPrice() == null ? 0 : o.getTotalPrice()).sum());
            orders.stream().filter(o -> o.getStatus() != OrderStatus.REJECTED && o.getDeliveryDate() != null).findFirst()
                    .ifPresent(o -> m.put("ultimoPedido", "N° " + o.getId() + " para el " + o.getDeliveryDate().format(DATE)
                            + " (" + AuditText.status(o.getStatus()) + ")"));
            // Cumpleaños sin el año: alcanza para saludar y no expone la edad
            if (c.getBirthDate() != null) m.put("cumpleanos", c.getBirthDate().format(DAY_MONTH));
            m.put("aceptaPromociones", Boolean.TRUE.equals(c.getMarketingConsent()));
            putIfNotNull(m, "telefono", BakeyPrivacy.phone(c.getPhone(), contact));
            putIfNotNull(m, "email", BakeyPrivacy.email(c.getEmail(), contact));
            putIfNotNull(m, "direccion", BakeyPrivacy.address(c.getAddress(), contact));
            list.add(m);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("encontrados", found.size());
        result.put("clientes", list);
        result.put("aviso", BakeyPrivacy.UNTRUSTED_NOTICE);
        if (found.size() > 10) result.put("hayMas", found.size() - 10);
        if (!contact) result.put("contactoTapado", true);
        return result;
    }

    private Map<String, Object> upcomingBirthdays(ToolContext ctx, ToolArgs args) {
        int days = args.integer("dias", 30, 1, 60);
        List<Map<String, Object>> list = new ArrayList<>();
        for (BirthdayService.UpcomingBirthday b : birthdayService.upcoming(days)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("cliente", BakeyPrivacy.untrusted(b.name()));
            m.put("fecha", b.date().format(DAY_MONTH) + " (" + b.date().format(WEEKDAY) + ")");
            m.put("faltanDias", b.daysLeft());
            m.put("yaSaludadoEsteAno", b.greetedThisYear());
            m.put("aceptaPromociones", b.consent());
            list.add(m);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dias", days);
        result.put("cantidad", list.size());
        result.put("cumpleanos", list.stream().limit(MAX_ITEMS).toList());
        result.put("aviso", BakeyPrivacy.UNTRUSTED_NOTICE);
        if (list.size() > MAX_ITEMS) result.put("hayMas", list.size() - MAX_ITEMS);
        return result;
    }

    // ---------------------------------------------------------------------
    // Inventario
    // ---------------------------------------------------------------------

    private Map<String, Object> stock(ToolContext ctx, ToolArgs args) {
        String text = args.text("texto", 60);
        boolean onlyLow = normalize(text).matches("baj[oa]s?|stock bajo|poco|faltante[s]?");
        String needle = normalize(text);
        List<InventoryItem> items = inventoryRepository.findAll().stream()
                .filter(i -> onlyLow ? isLow(i) : (needle.isBlank() || normalize(i.getName()).contains(needle)))
                .sorted(Comparator.comparing(InventoryItem::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("busqueda", onlyLow ? "stock bajo (en el mínimo o menos)" : (needle.isBlank() ? "todos" : text));
        result.put("cantidad", items.size());
        result.put("insumos", items.stream().limit(MAX_ITEMS).map(this::supply).toList());
        if (items.size() > MAX_ITEMS) result.put("hayMas", items.size() - MAX_ITEMS);
        return result;
    }

    private Map<String, Object> expiringSupplies(ToolContext ctx, ToolArgs args) {
        int days = args.integer("dias", 7, 1, 60);
        LocalDate limit = ctx.today().plusDays(days);
        List<InventoryItem> items = inventoryRepository.findAll().stream()
                .filter(i -> i.getExpirationDate() != null && !i.getExpirationDate().isAfter(limit))
                .sorted(Comparator.comparing(InventoryItem::getExpirationDate))
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("hasta", limit.format(DATE));
        result.put("cantidad", items.size());
        result.put("insumos", items.stream().limit(MAX_ITEMS).map(this::supply).toList());
        if (items.size() > MAX_ITEMS) result.put("hayMas", items.size() - MAX_ITEMS);
        return result;
    }

    // Un insumo para las listas (sin costos: el pastelero no ve plata y el modelo no los necesita)
    private Map<String, Object> supply(InventoryItem i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("insumo", i.getName());
        m.put("stock", round(i.getCurrentStock()));
        m.put("unidad", i.getUnit());
        m.put("minimo", round(i.getMinimumStock()));
        m.put("bajo", isLow(i));
        if (i.getExpirationDate() != null) {
            m.put("vence", i.getExpirationDate().format(DATE));
            m.put("vencido", i.getExpirationDate().isBefore(LocalDate.now()));
        }
        return m;
    }

    // Misma regla que el panel (inventory.js): bajo = stock actual menor o igual al mínimo
    private boolean isLow(InventoryItem i) {
        double stock = i.getCurrentStock() == null ? 0 : i.getCurrentStock();
        double min = i.getMinimumStock() == null ? 0 : i.getMinimumStock();
        return stock <= min;
    }

    /*
     * ¿Qué hay que comprar para los pedidos de un rango?
     * OJO con el stock: al CONFIRMAR un pedido el sistema ya descuenta sus insumos del inventario.
     * Entonces el stock que se ve hoy ya tiene restados los pedidos agendados y en preparación.
     * Lo que falta cubrir son los pedidos NUEVOS (sin confirmar). Si se sumaran también los agendados,
     * se contarían dos veces. Los agendados se informan aparte, solo como dato.
     */
    private Map<String, Object> suppliesForOrders(ToolContext ctx, ToolArgs args) {
        LocalDate from = args.date("desde");
        LocalDate to = args.date("hasta");
        ToolArgs.checkRange(from, to, MAX_RANGE_DAYS);

        List<Order> orders = orderRepository.findByDeliveryDateBetweenAndStatusIn(from, to, ACTIVE);
        List<Order> pending = orders.stream().filter(o -> o.getStatus() == OrderStatus.PENDING).toList();
        List<Order> scheduled = orders.stream().filter(o -> o.getStatus() != OrderStatus.PENDING).toList();
        Map<Long, Double> needPending = stockCalculator.requiredFor(pending);
        Map<Long, Double> needScheduled = stockCalculator.requiredFor(scheduled);

        // 1. Lo que necesitan los pedidos NUEVOS, comparado con el stock que se puede usar
        List<Map<String, Object>> supplies = new ArrayList<>();
        for (InventoryItem item : inventoryRepository.findAllById(needPending.keySet())) {
            double need = needPending.get(item.getId());
            double usable = stockCalculator.usableStock(item);
            double min = item.getMinimumStock() == null ? 0 : item.getMinimumStock();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("insumo", item.getName());
            m.put("unidad", item.getUnit());
            m.put("necesitan", round(need));
            m.put("stockUsable", round(usable));
            m.put("faltaComprar", round(Math.max(0, need - usable)));
            m.put("quedaDebajoDelMinimo", usable - need < min);
            if (usable == 0 && item.getCurrentStock() != null && item.getCurrentStock() > 0) m.put("vencido", true);
            supplies.add(m);
        }
        supplies.sort(Comparator.comparing(m -> -((Double) m.get("faltaComprar"))));

        // 2. Aparte y solo como dato: lo que usan los agendados (YA está descontado del stock, no hay que comprarlo)
        List<String> alreadyDeducted = new ArrayList<>();
        for (InventoryItem item : inventoryRepository.findAllById(needScheduled.keySet())) {
            alreadyDeducted.add(item.getName() + ": " + round(needScheduled.get(item.getId())) + " " + item.getUnit());
        }

        int withoutRecipe = orders.stream().mapToInt(stockCalculator::itemsWithoutRecipe).sum();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("desde", from.format(DATE));
        result.put("hasta", to.format(DATE));
        result.put("pedidosNuevos", pending.size());
        result.put("insumosParaPedidosNuevos", supplies);
        result.put("pedidosAgendadosOEnPreparacion", scheduled.size());
        result.put("yaDescontadoDelStockPorAgendados", alreadyDeducted);
        result.put("productosSinReceta", withoutRecipe);
        result.put("aclaracion", "Lo que hay que comprar es faltaComprar de insumosParaPedidosNuevos. Los pedidos "
                + "agendados ya descontaron sus insumos al confirmarse: NO hay que comprarlos ni restarlos otra vez. "
                + "Los productos sin receta (tortas del creador o pedidos escritos a mano) no se pueden calcular.");
        return result;
    }

    // ---------------------------------------------------------------------
    // Ventas (solo ADMIN)
    // ---------------------------------------------------------------------

    private Map<String, Object> salesSummary(ToolContext ctx, ToolArgs args) {
        LocalDate from = args.date("desde");
        LocalDate to = args.date("hasta");
        ToolArgs.checkRange(from, to, 366);

        List<Order> orders = orderRepository.findByDeliveryDateBetweenAndStatusIn(from, to, SOLD);
        double billed = 0, collected = 0, costedSales = 0, costedCost = 0;
        int costed = 0;
        Map<String, Long> products = new HashMap<>();
        for (Order o : orders) {
            double total = o.getTotalPrice() == null ? 0 : o.getTotalPrice();
            double deposit = o.getDeposit() == null ? 0 : o.getDeposit();
            billed += total;
            // Entregado = cobrado entero; si no, lo cobrado es la seña (como el Resumen del panel)
            collected += o.getStatus() == OrderStatus.COMPLETED ? total : Math.min(deposit, total);
            if (o.getItems() != null) {
                o.getItems().forEach(i -> products.merge(itemName(i), 1L, Long::sum));
            }
            // Ganancia estimada: solo si TODOS los productos del pedido tienen receta con costo completo
            Double cost = recipeCost(o);
            if (cost != null) {
                costed++;
                costedSales += total;
                costedCost += cost;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("desde", from.format(DATE));
        result.put("hasta", to.format(DATE));
        result.put("pedidos", orders.size());
        result.put("facturado", round(billed));
        result.put("cobrado", round(collected));
        result.put("faltaCobrar", round(billed - collected));
        result.put("ticketPromedio", orders.isEmpty() ? 0 : round(billed / orders.size()));
        result.put("masPedidos", products.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(5)
                .map(e -> e.getKey() + " (" + e.getValue() + ")").toList());
        Map<String, Object> profit = new LinkedHashMap<>();
        profit.put("pedidosConCostoCompleto", costed);
        profit.put("ventasDeEsosPedidos", round(costedSales));
        profit.put("costoDeInsumos", round(costedCost));
        profit.put("gananciaEstimada", round(costedSales - costedCost));
        result.put("gananciaEstimada", profit);
        result.put("aclaracion", "Cuenta los pedidos agendados, en preparación y entregados, por fecha de entrega. "
                + "La ganancia solo incluye pedidos cuyos productos tienen receta con costo cargado "
                + "(las tortas del creador no tienen receta).");
        return result;
    }

    // Costo de insumos del pedido según las recetas; null si algún producto no tiene receta con costo completo
    private Double recipeCost(Order o) {
        if (o.getItems() == null || o.getItems().isEmpty()) return null;
        double cost = 0;
        for (OrderItem item : o.getItems()) {
            Product p = item.getProduct();
            if (p == null || p.getRecipe() == null || !p.getRecipe().isCostComplete()) return null;
            cost += p.getRecipe().getEstimatedCost();
        }
        return cost;
    }

    // ---------------------------------------------------------------------
    // Productos y recordatorios
    // ---------------------------------------------------------------------

    private Map<String, Object> productsAndPrices(ToolContext ctx, ToolArgs args) {
        List<Map<String, Object>> products = productRepository.findAll().stream()
                .filter(Product::isEnabled)
                .sorted(Comparator.comparing(Product::getName, String.CASE_INSENSITIVE_ORDER))
                .map(p -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("producto", p.getName());
                    if (p.getCategory() != null) m.put("categoria", p.getCategory().getName());
                    m.put("precio", p.getPrice());
                    return m;
                }).toList();
        List<Map<String, Object>> sizes = cakeSizeRepository.findByEnabledTrueOrderByIdAsc().stream()
                .map(s -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("tamano", s.getName());
                    m.put("precio", s.getPrice());
                    if (s.getMaxRellenos() != null) m.put("rellenosMaximos", s.getMaxRellenos());
                    return m;
                }).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("productos", products.stream().limit(MAX_ITEMS * 2).toList());
        result.put("tortasPersonalizadas", sizes);
        return result;
    }

    private Map<String, Object> myReminders(ToolContext ctx, ToolArgs args) {
        // Siempre los propios, aunque sea ADMIN (el nombre de la herramienta es "mis" recordatorios)
        List<Reminder> reminders = reminderService.list(ctx.user(), false, ReminderService.Scope.PENDING);
        List<Map<String, Object>> list = reminders.stream().limit(MAX_ITEMS).map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());   // para poder marcarlo como hecho (marcar_recordatorio_hecho)
            m.put("titulo", r.getTitle());
            m.put("cuando", r.getDueAt().format(DATE_TIME) + " (" + r.getDueAt().format(WEEKDAY) + ")");
            m.put("vencido", r.getDueAt().isBefore(java.time.LocalDateTime.now()));
            if (r.getRelatedOrderId() != null) m.put("pedido", r.getRelatedOrderId());
            if (r.getNotes() != null) m.put("notas", cut(r.getNotes(), 200));
            return m;
        }).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cantidad", reminders.size());
        result.put("recordatorios", list);
        if (reminders.size() > MAX_ITEMS) result.put("hayMas", reminders.size() - MAX_ITEMS);
        return result;
    }

    // ---------------------------------------------------------------------

    // Minúsculas y sin tildes, para buscar "Maria" y encontrar "María"
    static String normalize(String text) {
        if (text == null) return "";
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }

    private static double round(Double value) {
        if (value == null) return 0;
        return Math.round(value * 100.0) / 100.0;
    }

    private static String cut(String text, int max) {
        return text.length() > max ? text.substring(0, max) + "…" : text;
    }

    private static void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) map.put(key, value);
    }
}
