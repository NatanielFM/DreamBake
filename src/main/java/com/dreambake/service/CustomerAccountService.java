package com.dreambake.service;

import com.dreambake.entity.AuditAction;
import com.dreambake.entity.AuditModule;
import com.dreambake.audit.AuditText;
import java.util.LinkedHashMap;
import java.util.Map;
import com.dreambake.entity.Customer;
import com.dreambake.entity.EmailLog;
import com.dreambake.entity.Order;
import com.dreambake.entity.OrderItem;
import com.dreambake.entity.OrderStatus;
import com.dreambake.entity.User;
import com.dreambake.repository.CustomerRepository;
import com.dreambake.repository.CustomerTagRepository;
import com.dreambake.repository.EmailLogRepository;
import com.dreambake.repository.OrderRepository;
import com.dreambake.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Área del cliente (paso 2.4): lo que un cliente logueado puede ver y hacer con sus propios datos.
 *
 *  - Su ficha de cliente (Customer): se busca por el EMAIL DE SU CUENTA. Solo si ese email está
 *    comprobado (User.emailVerified): si no, alguien podría registrarse con el email de otra persona
 *    y ver o cambiar sus datos.
 *  - "Mis pedidos": los pedidos unidos a una ficha con su email y los nuevos (todavía sin ficha)
 *    hechos con su email.
 *  - "Eliminar mi cuenta" (derecho de supresión, Ley 25.326): borra el usuario y saca los datos
 *    personales de la ficha y de los pedidos, pero los pedidos quedan (productos, fechas y montos)
 *    para la contabilidad del negocio.
 *
 * Todo se calcula siempre a partir del usuario logueado: nunca se recibe un email de afuera.
 */
@Service
public class CustomerAccountService {

    // Nombre que queda en la ficha y en los pedidos de una cuenta eliminada
    public static final String DELETED_NAME = "Cliente eliminado";

    // Pedidos "en curso": mientras haya alguno, no se puede eliminar la cuenta (el negocio necesita
    // el contacto para entregarlo)
    private static final Set<OrderStatus> ACTIVE = EnumSet.of(OrderStatus.PENDING, OrderStatus.CONFIRMED,
            OrderStatus.IN_PROGRESS);

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CustomerTagRepository tagRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private EmailLogRepository emailLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditService auditService;

    /** Un pedido como lo ve el cliente en "Mis pedidos" (solo lo necesario, sin datos internos). */
    public record MyOrder(Long id, LocalDate deliveryDate, String deliveryShift, String deliveryMethod,
                          String status, String statusLabel, Double total, List<String> items) {}

    /** ¿Se puede usar el área del cliente? Solo clientes con el email comprobado. */
    public boolean canUseCustomerArea(User user) {
        return user != null && !SocialLoginService.isStaff(user) && Boolean.TRUE.equals(user.getEmailVerified());
    }

    /** Su ficha de cliente (la primera con el email de su cuenta), si existe. */
    public Optional<Customer> findCustomer(User user) {
        if (!canUseCustomerArea(user)) return Optional.empty();
        return customerRepository.findByEmailIgnoreCase(user.getEmail()).stream().findFirst();
    }

    /**
     * Guarda "Datos personales" del perfil. Nombre y teléfono van al usuario; si el email está
     * comprobado, además se actualiza (o se crea) su ficha de cliente con nombre, teléfono,
     * cumpleaños y consentimiento para promociones (con la fecha en que lo aceptó).
     */
    @Transactional
    public void saveProfile(User user, String name, String phone, LocalDate birthDate, boolean marketingConsent) {
        Map<String, Object> before = profileSnapshot(user, findCustomer(user).orElse(null)); // para la auditoría
        user.setName(name);
        user.setPhone(phone);
        userRepository.save(user);

        if (!canUseCustomerArea(user)) {
            logProfile(user, before, null);
            return;
        }

        Customer customer = findCustomer(user).orElseGet(() -> {
            Customer c = new Customer();
            c.setEmail(user.getEmail());
            // Igual que los clientes que se crean al confirmar un pedido (OrderRestController.linkCustomer)
            c.setTag(tagRepository.findByName("Sin etiqueta").orElse(null));
            return c;
        });
        customer.setName(name);
        if (phone != null && !phone.isBlank()) customer.setPhone(phone);
        customer.setBirthDate(validBirthDate(birthDate));

        // Consentimiento: acá la casilla es una preferencia explícita del cliente, así que también
        // puede sacarlo. Al aceptarlo se guarda la fecha (constancia, Ley 25.326).
        if (marketingConsent && !Boolean.TRUE.equals(customer.getMarketingConsent())) {
            customer.setMarketingConsent(true);
            customer.setConsentDate(LocalDateTime.now());
        } else if (!marketingConsent) {
            customer.setMarketingConsent(false);
            customer.setConsentDate(null);
        }
        customerRepository.save(customer);
        logProfile(user, before, customer);
    }

    // Auditoría de "Datos personales": qué cambió (el consentimiento queda como constancia, Ley 25.326)
    private void logProfile(User user, Map<String, Object> before, Customer customer) {
        Map<String, Object> diff = AuditText.diff(before, profileSnapshot(user, customer));
        auditService.log(AuditAction.UPDATE, AuditModule.CLIENTES, "Usuario", user.getId(),
                "Actualizó sus datos personales" + AuditText.changedFields(diff), diff);
    }

    private static Map<String, Object> profileSnapshot(User user, Customer customer) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nombre", user.getName());
        m.put("telefono", user.getPhone());
        m.put("cumpleanos", customer == null || customer.getBirthDate() == null ? null : customer.getBirthDate().toString());
        m.put("promociones", customer != null && Boolean.TRUE.equals(customer.getMarketingConsent()));
        return m;
    }

    /** "Mis pedidos" del usuario logueado (vacío si su email no está comprobado). */
    @Transactional(readOnly = true)
    public List<MyOrder> myOrders(User user) {
        if (!canUseCustomerArea(user)) return List.of();
        return orderRepository.findForAccount(user.getEmail()).stream()
                .map(o -> new MyOrder(o.getId(), o.getDeliveryDate(), o.getDeliveryShift(), o.getDeliveryMethod(),
                        o.getStatus() == null ? null : o.getStatus().name(), statusLabel(o.getStatus()),
                        o.getTotalPrice(), o.getItems().stream().map(this::itemName).toList()))
                .toList();
    }

    /** Pedidos en curso (si hay alguno no se puede eliminar la cuenta). */
    @Transactional(readOnly = true)
    public List<Order> activeOrders(User user) {
        if (!canUseCustomerArea(user)) return List.of();
        return orderRepository.findForAccount(user.getEmail()).stream()
                .filter(o -> ACTIVE.contains(o.getStatus()))
                .toList();
    }

    /**
     * Elimina la cuenta de un cliente. Devuelve false (sin cambiar nada) si tiene pedidos en curso.
     * Si el email no está comprobado, solo se borra el usuario: no tocamos fichas ni pedidos que
     * no sabemos si son suyos.
     */
    @Transactional
    public boolean deleteAccount(User user) {
        if (SocialLoginService.isStaff(user)) {
            throw new IllegalStateException("El personal no puede eliminar su cuenta desde el perfil");
        }
        if (!activeOrders(user).isEmpty()) {
            return false;
        }
        if (canUseCustomerArea(user)) {
            String email = user.getEmail();
            // 1. Pedidos: quedan para la contabilidad, pero sin datos personales
            for (Order order : orderRepository.findForAccount(email)) {
                order.setCustomerName(DELETED_NAME);
                order.setCustomerPhone(null);
                order.setCustomerEmail(null);
                order.setDeliveryAddress(null);
                order.setCustomerBirthDate(null);
                order.setMarketingConsent(false);
                order.setMarketingConsentAt(null);
                orderRepository.save(order);
            }
            // 2. Fichas de cliente: se anonimizan (no se borran, porque los pedidos las siguen usando)
            for (Customer customer : customerRepository.findByEmailIgnoreCase(email)) {
                customer.setName(DELETED_NAME);
                customer.setEmail(null);
                customer.setPhone(null);
                customer.setAddress(null);
                customer.setBirthDate(null);
                customer.setMarketingConsent(false);
                customer.setConsentDate(null);
                customer.setUnsubscribeToken(UUID.randomUUID().toString()); // el link de baja viejo deja de servir
                customerRepository.save(customer);
            }
            // 3. Historial de emails: se borra la dirección
            for (EmailLog log : emailLogRepository.findByRecipientEmailIgnoreCase(email)) {
                log.setRecipientEmail(null);
                emailLogRepository.save(log);
            }
        }
        // 4. Auditoría (antes de borrar el usuario, así figura su nombre) y 5. el usuario
        auditService.log(AuditAction.DELETE, AuditModule.SEGURIDAD, "Usuario", user.getId(),
                "Eliminó su cuenta de cliente (derecho de supresión, Ley 25.326)", null);
        userRepository.delete(user);
        return true;
    }

    // Estado del pedido en palabras simples para el cliente
    public static String statusLabel(OrderStatus status) {
        if (status == null) return "Recibido";
        return switch (status) {
            case PENDING -> "Recibido, falta confirmar";
            case CONFIRMED -> "Confirmado";
            case IN_PROGRESS -> "En preparación";
            case COMPLETED -> "Entregado";
            case CANCELLED -> "Cancelado";
            case REJECTED -> "No lo pudimos tomar";
        };
    }

    private String itemName(OrderItem item) {
        if (Boolean.TRUE.equals(item.getIsCustomCake())) return "Torta personalizada";
        if (item.getProduct() != null) return item.getProduct().getName();
        return item.getProductType() != null ? item.getProductType() : "Producto";
    }

    // Fecha de cumpleaños razonable (entre 1900 y hoy); si no lo es, se descarta (es un dato opcional)
    private static LocalDate validBirthDate(LocalDate date) {
        if (date == null) return null;
        if (date.isAfter(LocalDate.now()) || date.isBefore(LocalDate.of(1900, 1, 1))) return null;
        return date;
    }
}
