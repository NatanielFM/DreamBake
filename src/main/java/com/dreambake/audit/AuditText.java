package com.dreambake.audit;

import com.dreambake.entity.Order;
import com.dreambake.entity.OrderItem;
import com.dreambake.entity.OrderStatus;

import java.text.NumberFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Textos comunes de la auditoría: los estados como los ve la dueña en el panel, la plata en pesos
 * y la "foto" de un pedido (para guardar el antes y el después).
 */
public final class AuditText {

    private AuditText() {}


    /** El estado con el mismo nombre que muestra el panel (orders.js / clients.js). */
    public static String status(OrderStatus status) {
        if (status == null) return "sin estado";
        return switch (status) {
            case PENDING -> "Nuevo";
            case CONFIRMED -> "Agendado";
            case IN_PROGRESS -> "En preparación";
            case COMPLETED -> "Entregado";
            case CANCELLED -> "Cancelado";
            case REJECTED -> "Rechazado";
        };
    }

    /**
     * Precio con formato argentino (paso 6.8, igual que DreamFormat.money del JS):
     * 15000 → "$ 15.000"; 15000.5 → "$ 15.000,50" (centavos solo si los tiene).
     */
    public static String money(Double amount) {
        double v = amount == null ? 0 : amount;
        boolean cents = Math.round(Math.abs(v) * 100) % 100 != 0;
        NumberFormat f = NumberFormat.getNumberInstance(Locale.of("es", "AR"));
        f.setMinimumFractionDigits(cents ? 2 : 0);
        f.setMaximumFractionDigits(cents ? 2 : 0);
        return "$ " + f.format(v);
    }

    /** 5.0 → "5", 2.5 → "2,5" (cantidades de stock en las descripciones) */
    public static String number(Number value) {
        if (value == null) return "0";
        NumberFormat f = NumberFormat.getNumberInstance(Locale.of("es", "AR"));
        f.setMaximumFractionDigits(3);
        return f.format(value);
    }

    /** Nombres en español para la pantalla y las descargas del registro de actividad. */
    public static String moduleLabel(com.dreambake.entity.AuditModule m) {
        if (m == null) return "";
        return switch (m) {
            case PEDIDOS -> "Pedidos";
            case PRODUCTOS -> "Productos";
            case INVENTARIO -> "Inventario";
            case RECETAS -> "Recetas";
            case CLIENTES -> "Clientes";
            case CUPONES -> "Cupones";
            case GALERIA -> "Galería";
            case EQUIPO -> "Equipo";
            case ARMADOR -> "Armador de tortas";
            case FIDELIZACION -> "Fidelización";
            case SISTEMA -> "Sistema";
            case SEGURIDAD -> "Seguridad";
            case BAKEY -> "Bakey";
            case RECORDATORIOS -> "Recordatorios";
        };
    }

    public static String actionLabel(com.dreambake.entity.AuditAction a) {
        if (a == null) return "";
        return switch (a) {
            case CREATE -> "Alta";
            case UPDATE -> "Modificación";
            case DELETE -> "Baja";
            case STATUS_CHANGE -> "Cambio de estado";
            case LOGIN -> "Inicio de sesión";
            case LOGIN_FAILED -> "Ingreso fallido";
            case LOGOUT -> "Cierre de sesión";
            case EXPORT -> "Exportación";
            case DOWNLOAD -> "Descarga";
            case CONFIG_CHANGE -> "Configuración";
            case EMAIL_CAMPAIGN -> "Envío de emails";
            case BACKUP -> "Copia de seguridad";
            case RESTORE -> "Restauración";
            case ASSISTANT_ACTION -> "Acción de Bakey";
        };
    }

    public static String roleLabel(String role) {
        if (role == null) return "";
        return switch (role) {
            case "ADMIN" -> "Administrador";
            case "PASTELERO" -> "Pastelero";
            case "ROLE_USER" -> "Cliente";
            case "PUBLICO" -> "Sin sesión";
            case "SISTEMA" -> "Sistema";
            default -> role;
        };
    }

    // ===== Datos personales enmascarados (revisión de la fase 3) =====
    // El registro de auditoría es inmutable: lo que se guarda ahí no se puede borrar nunca, ni siquiera
    // cuando un cliente usa "Eliminar mi cuenta" (derecho de supresión, Ley 25.326). Por eso los datos de
    // contacto se guardan TAPADOS: se ve que cambiaron y quién lo hizo, pero no el dato completo.

    /** "3764555666" → "3764•••666" */
    public static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) return phone;
        String p = phone.trim();
        if (p.length() <= 6) return "•••";
        return p.substring(0, 4) + "•••" + p.substring(p.length() - 3);
    }

    /** "maria@gmail.com" → "m•••@gmail.com" */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) return email;
        int at = email.indexOf('@');
        if (at <= 0) return "•••";
        return email.charAt(0) + "•••" + email.substring(at);
    }

    /** "Calle Falsa 123" → "Calle•••" (solo la primera palabra) */
    public static String maskText(String text) {
        if (text == null || text.isBlank()) return text;
        String first = text.trim().split("\\s+")[0];
        return (first.length() > 12 ? first.substring(0, 12) : first) + "•••";
    }

    /** "1991-03-15" → "••/••/1991" (queda el año) */
    public static String maskDate(String date) {
        if (date == null || date.length() < 4) return date;
        return "••/••/" + date.substring(0, 4);
    }

    /** Enmascara según el nombre del dato (lo usa AuditService al guardar los detalles). */
    public static Object maskPersonal(String key, Object value) {
        if (!(value instanceof String s)) return value;
        return switch (key) {
            case "telefono" -> maskPhone(s);
            case "email" -> maskEmail(s);
            case "direccion" -> maskText(s);
            case "cumpleanos" -> maskDate(s);
            default -> value;
        };
    }

    /** "#45 de María Gómez" */
    public static String order(Order order) {
        String name = order.getCustomerName() == null || order.getCustomerName().isBlank() ? "sin nombre" : order.getCustomerName();
        return "#" + order.getId() + " de " + name;
    }

    /** Los datos de un pedido que importan para comparar el antes y el después. */
    public static Map<String, Object> snapshot(Order o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cliente", o.getCustomerName());
        m.put("telefono", o.getCustomerPhone());
        m.put("email", o.getCustomerEmail());
        m.put("entrega", o.getDeliveryDate() == null ? null : o.getDeliveryDate().toString());
        m.put("turno", o.getDeliveryShift());
        m.put("metodo", o.getDeliveryMethod());
        m.put("direccion", o.getDeliveryAddress());
        m.put("estado", status(o.getStatus()));
        m.put("total", o.getTotalPrice());
        m.put("sena", o.getDeposit());
        m.put("productos", o.getItems() == null ? List.of() : o.getItems().stream().map(AuditText::itemText).toList());
        return m;
    }

    /** {"antes": {...}, "despues": {...}} */
    public static Map<String, Object> change(Object before, Object after) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("antes", before);
        m.put("despues", after);
        return m;
    }

    /**
     * Compara dos "fotos" y se queda solo con lo que cambió:
     * {"antes": {"total": 15000}, "despues": {"total": 18000}}. Más fácil de leer que todo completo.
     */
    public static Map<String, Object> diff(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> was = new LinkedHashMap<>();
        Map<String, Object> now = new LinkedHashMap<>();
        for (String key : after.keySet()) {
            Object a = before.get(key), b = after.get(key);
            if (a == null ? b != null : !a.equals(b)) {
                was.put(key, a);
                now.put(key, b);
            }
        }
        return change(was, now);
    }

    /** ": cambió total, fecha de entrega" — lo que cambió, para la descripción (o " (sin cambios)"). */
    @SuppressWarnings("unchecked")
    public static String changedFields(Map<String, Object> diff) {
        Map<String, Object> after = (Map<String, Object>) diff.get("despues");
        if (after == null || after.isEmpty()) return " (sin cambios)";
        return ": cambió " + String.join(", ", after.keySet().stream().map(AuditText::fieldName).toList());
    }

    private static String fieldName(String key) {
        return switch (key) {
            case "telefono" -> "teléfono";
            case "entrega" -> "fecha de entrega";
            case "metodo" -> "retiro/envío";
            case "direccion" -> "dirección";
            case "sena" -> "seña";
            case "minimo" -> "stock mínimo";
            case "categoria" -> "categoría";
            case "descripcion" -> "descripción";
            case "visible" -> "si se muestra";
            case "limite" -> "límite de usos";
            case "codigo" -> "código";
            case "rellenos" -> "cantidad de rellenos";
            case "mezclaBizcochuelos" -> "mezcla de bizcochuelos";
            case "habilitado" -> "si puede entrar";
            case "cumpleanos" -> "cumpleaños";
            case "promociones" -> "permiso para promociones";
            case "saludosAutomaticos" -> "saludos automáticos";
            case "descuentoCumpleanos" -> "descuento de cumpleaños";
            case "maximoEmailsPorDia" -> "máximo de emails por día";
            default -> key;
        };
    }

    private static String itemText(OrderItem item) {
        String name = item.getProductType() != null ? item.getProductType()
                : item.getProduct() != null ? item.getProduct().getName() : "Producto";
        return name + " (" + money(item.getPrice()) + ")";
    }
}
