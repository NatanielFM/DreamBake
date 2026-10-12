package com.dreambake.controller;

import com.dreambake.audit.Auditable;
import com.dreambake.entity.AuditAction;
import com.dreambake.entity.AuditModule;
import com.dreambake.service.AuditService;
import com.dreambake.entity.Campaign;
import com.dreambake.entity.Customer;
import com.dreambake.entity.CustomerTag;
import com.dreambake.entity.EmailStatus;
import com.dreambake.entity.EmailType;
import com.dreambake.entity.Order;
import com.dreambake.entity.OrderStatus;
import com.dreambake.repository.CampaignRepository;
import com.dreambake.repository.CustomerRepository;
import com.dreambake.repository.CustomerTagRepository;
import com.dreambake.repository.EmailLogRepository;
import com.dreambake.repository.OrderRepository;
import com.dreambake.service.CampaignService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class CustomerRestController {

    @Autowired
    private CustomerRepository customerRepo;

    // Registro de auditoría (paso 3.3): se usa directo donde hace falta el antes y el después
    @Autowired
    private AuditService audit;

    @Autowired
    private CustomerTagRepository tagRepo;

    @Autowired
    private OrderRepository orderRepo;

    @Autowired
    private EmailLogRepository emailLogRepo;

    @Autowired
    private CampaignRepository campaignRepo;

    // Pedidos que cuentan como compra (los nuevos sin confirmar, cancelados y rechazados no)
    private static final List<OrderStatus> PURCHASE_STATUSES =
            List.of(OrderStatus.CONFIRMED, OrderStatus.IN_PROGRESS, OrderStatus.COMPLETED);

    // ================= CLIENTES =================

    @GetMapping("/customers")
    public List<Customer> getAllCustomers() {
        List<Customer> customers = customerRepo.findAll();

        // Cantidad de pedidos y total gastado de cada cliente. Un pedido es de un cliente si
        // está unido a él (relación Pedido–Cliente) o, en los pedidos viejos, si tiene su teléfono.
        Map<Long, Customer> byId = new HashMap<>();
        Map<String, Customer> byPhone = new HashMap<>();
        for (Customer c : customers) {
            c.setOrdersCount(0);
            c.setTotalSpent(0.0);
            byId.put(c.getId(), c);
            if (c.getPhone() != null && !c.getPhone().isBlank()) byPhone.putIfAbsent(c.getPhone().trim(), c);
        }
        for (Order o : orderRepo.findAll()) {
            if (!PURCHASE_STATUSES.contains(o.getStatus())) continue;
            Customer owner = o.getCustomer() != null ? byId.get(o.getCustomer().getId())
                    : (o.getCustomerPhone() != null ? byPhone.get(o.getCustomerPhone().trim()) : null);
            if (owner == null) continue;
            owner.setOrdersCount(owner.getOrdersCount() + 1);
            owner.setTotalSpent(owner.getTotalSpent() + (o.getTotalPrice() != null ? o.getTotalPrice() : 0));
        }
        return customers;
    }

    // Historial de pedidos de un cliente (botón "Pedidos" de la tabla de Clientes)
    @GetMapping("/customers/{id}/orders")
    public ResponseEntity<List<Order>> getCustomerOrders(@PathVariable Long id) {
        return customerRepo.findById(id)
                .map(c -> ResponseEntity.ok(orderRepo.findByCustomerOrCustomerPhoneOrderByDeliveryDateDesc(
                        c, c.getPhone() != null && !c.getPhone().isBlank() ? c.getPhone().trim() : null)))
                .orElse(ResponseEntity.notFound().build());
    }

    // Dar de baja de los emails promocionales (botón "Dar de baja" de la ficha del cliente).
    // Es lo ÚNICO que el panel puede hacer con el consentimiento: desactivarlo. Activarlo solo
    // lo puede hacer el propio cliente, tildando la casilla en la tienda (Ley 25.326).
    @PostMapping("/customers/{id}/unsubscribe")
    @Auditable(action = AuditAction.UPDATE, module = AuditModule.FIDELIZACION, entityType = "Cliente", entityId = "#id",
            description = "Dio de baja de los emails de promociones a «#{#body.name}»")
    public ResponseEntity<Customer> unsubscribeCustomer(@PathVariable Long id) {
        return customerRepo.findById(id).map(c -> {
            c.setMarketingConsent(false);
            return ResponseEntity.ok(customerRepo.save(c));
        }).orElse(ResponseEntity.notFound().build());
    }

    // Lo que se muestra de cada email en la ficha del cliente
    public record EmailHistoryItem(LocalDateTime sentAt, String type, String subject, String status, String error) {
    }

    // Historial de emails de un cliente, del más nuevo al más viejo
    @GetMapping("/customers/{id}/emails")
    public ResponseEntity<List<EmailHistoryItem>> getCustomerEmails(@PathVariable Long id) {
        return customerRepo.findById(id)
                .map(c -> ResponseEntity.ok(emailLogRepo.findByCustomerOrderBySentAtDesc(c).stream()
                        .map(e -> new EmailHistoryItem(e.getSentAt(), typeLabel(e.getType()), e.getSubject(),
                                e.getStatus() == EmailStatus.SENT ? "Enviado" : "No se envió", e.getErrorMessage()))
                        .toList()))
                .orElse(ResponseEntity.notFound().build());
    }

    // Nombre en español de cada tipo de email (para la ficha del cliente)
    private String typeLabel(EmailType type) {
        if (type == null) return "Email";
        return switch (type) {
            case BIRTHDAY -> "Cumpleaños";
            case PROMOTION -> "Promoción";
            case ORDER_RECEIVED -> "Pedido recibido";
            case ORDER_CONFIRMATION -> "Pedido confirmado";
            case ORDER_READY -> "Pedido listo";
            case ORDER_CANCELLED -> "Pedido cancelado";
            case ORDER_REMINDER -> "Recordatorio de pedido";
            case NEW_ORDER_ALERT -> "Aviso de nuevo pedido";
            case DELIVERY_SUMMARY -> "Resumen de entregas";
            case TEST -> "Prueba";
            case ACCOUNT_VERIFICATION -> "Código de verificación";
            case PASSWORD_RESET -> "Recuperar contraseña";
            case PASSWORD_CHANGED -> "Contraseña cambiada";
            case AUDIT_ALERT -> "Alerta de auditoría";
            case BACKUP_ALERT -> "Alerta de copia de seguridad";
            case TASK_REMINDER -> "Recordatorio de tarea";
        };
    }

    @PostMapping("/customers")
    @Auditable(action = AuditAction.CREATE, module = AuditModule.CLIENTES, entityType = "Cliente", entityId = "#result.id",
            description = "Agregó al cliente «#{#result.name}»")
    public Customer createCustomer(@Valid @RequestBody CustomerRequest request) {
        return saveOrUpdateCustomer(new Customer(), request);
    }

    @PutMapping("/customers/{id}")
    @Auditable(action = AuditAction.UPDATE, module = AuditModule.CLIENTES, entityType = "Cliente", entityId = "#id",
            description = "Editó la ficha del cliente «#{#body.name}»")
    public ResponseEntity<Customer> updateCustomer(@PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
        return customerRepo.findById(id)
                .map(customer -> ResponseEntity.ok(saveOrUpdateCustomer(customer, request)))
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/customers/{id}")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Void> deleteCustomer(@PathVariable Long id) {
        return customerRepo.findById(id).map(customer -> {
            // Los pedidos del cliente NO se borran: solo se desvinculan (siguen con su nombre y teléfono)
            for (Order o : orderRepo.findByCustomer(customer)) {
                o.setCustomer(null);
            }
            // Lo mismo con su historial de emails: si no, la base no deja borrar al cliente
            // (email_logs apunta a él) y el panel mostraba un error
            for (com.dreambake.entity.EmailLog log : emailLogRepo.findByCustomerOrderBySentAtDesc(customer)) {
                log.setCustomer(null);
            }
            customerRepo.delete(customer);
            audit.log(AuditAction.DELETE, AuditModule.CLIENTES, "Cliente", id,
                    "Eliminó al cliente «" + customer.getName() + "» (sus pedidos se conservan)", null);
            return ResponseEntity.ok().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }

    private Customer saveOrUpdateCustomer(Customer customer, CustomerRequest req) {
        customer.setName(req.name());
        // El cliente aceptó promociones para SU email: si acá se cambia por otro, ese permiso
        // no pasa al email nuevo (el cliente lo puede volver a dar en su próximo pedido)
        String oldEmail = customer.getEmail() == null ? "" : customer.getEmail().trim();
        String newEmail = req.email() == null ? "" : req.email().trim();
        if (Boolean.TRUE.equals(customer.getMarketingConsent()) && !oldEmail.equalsIgnoreCase(newEmail)) {
            customer.setMarketingConsent(false);
        }
        customer.setEmail(req.email());
        customer.setPhone(req.phone());
        customer.setAddress(req.address());
        // Cumpleaños: el administrador lo puede cargar o corregir (no puede ser una fecha futura)
        customer.setBirthDate(req.birthDate() != null && req.birthDate().isAfter(LocalDate.now()) ? null : req.birthDate());
        // OJO: el consentimiento de emails NO se toca acá (ver unsubscribeCustomer)

        CustomerTag tag = tagRepo.findByName(req.tagName())
                .orElseGet(() -> tagRepo.findByName("Sin etiqueta").orElse(null));
        customer.setTag(tag);

        return customerRepo.save(customer);
    }

    public record CustomerRequest(
            @NotBlank(message = "El nombre del cliente es obligatorio.") String name,
            String email, String phone, String address, String tagName, LocalDate birthDate) {
    }

    // ================= ETIQUETAS (TAGS) =================

    @GetMapping("/tags")
    public List<CustomerTag> getAllTags() {
        return tagRepo.findAll(Sort.by(Sort.Direction.ASC, "displayOrder"));
    }

    @PostMapping("/tags")
    @Auditable(action = AuditAction.CREATE, module = AuditModule.CLIENTES, entityType = "Etiqueta", entityId = "#body.id",
            description = "Creó la etiqueta de clientes «#{#body.name}»")
    public ResponseEntity<?> createTag(@RequestBody CustomerTag tag) {
        if (tagRepo.findByName(tag.getName()).isPresent()) {
            return ResponseEntity.badRequest().body("La etiqueta ya existe.");
        }
        long count = tagRepo.count();
        tag.setDisplayOrder((int) count);
        return ResponseEntity.ok(tagRepo.save(tag));
    }

    @PutMapping("/tags/{id}")
    @Auditable(action = AuditAction.UPDATE, module = AuditModule.CLIENTES, entityType = "Etiqueta", entityId = "#id",
            description = "Cambió el nombre de una etiqueta de clientes a «#{#body.name}»")
    public ResponseEntity<CustomerTag> updateTag(@PathVariable Long id, @RequestBody CustomerTag request) {
        return tagRepo.findById(id).map(tag -> {
            tag.setName(request.getName());
            return ResponseEntity.ok(tagRepo.save(tag));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/tags/{id}")
    public ResponseEntity<?> deleteTag(@PathVariable Long id) {
        return tagRepo.findById(id).map(tag -> {
            if (!tag.isDeletable()) {
                return ResponseEntity.badRequest().body("No se puede eliminar esta etiqueta por defecto.");
            }
            // Si la usa una campaña que todavía no terminó, no se deja borrar (se quedaría sin destinatarios)
            if (campaignRepo.existsByTagAndStatusIn(tag, CampaignService.UNFINISHED)) {
                return ResponseEntity.badRequest().body(
                        "Esta etiqueta la usa una campaña que todavía no se terminó de enviar. Cambiá la audiencia de la campaña antes de borrarla.");
            }
            // En las campañas ya terminadas solo se le quita la referencia (el historial queda)
            for (Campaign c : campaignRepo.findByTag(tag)) {
                c.setTag(null);
                campaignRepo.save(c);
            }
            CustomerTag defaultTag = tagRepo.findByName("Sin etiqueta").orElse(null);
            List<Customer> customers = customerRepo.findByTag(tag);
            for (Customer c : customers) {
                c.setTag(defaultTag);
                customerRepo.save(c);
            }
            tagRepo.delete(tag);
            audit.log(AuditAction.DELETE, AuditModule.CLIENTES, "Etiqueta", id,
                    "Eliminó la etiqueta de clientes «" + tag.getName() + "» (" + customers.size() + (customers.size() == 1 ? " cliente pasó" : " clientes pasaron") + " a «Sin etiqueta»)", null);
            return ResponseEntity.ok().build();
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/tags/reorder")
    @Auditable(action = AuditAction.UPDATE, module = AuditModule.CLIENTES, entityType = "Etiqueta",
            description = "Cambió el orden de las etiquetas de clientes")
    public ResponseEntity<?> reorderTags(@RequestBody List<Long> orderedIds) {
        for (int i = 0; i < orderedIds.size(); i++) {
            Long id = orderedIds.get(i);
            Optional<CustomerTag> optTag = tagRepo.findById(id);
            if (optTag.isPresent()) {
                CustomerTag tag = optTag.get();
                tag.setDisplayOrder(i);
                tagRepo.save(tag);
            }
        }
        return ResponseEntity.ok().build();
    }
}