package com.dreambake.controller;

import com.dreambake.entity.Customer;
import com.dreambake.entity.CustomerTag;
import com.dreambake.entity.Order;
import com.dreambake.entity.OrderStatus;
import com.dreambake.repository.CustomerRepository;
import com.dreambake.repository.CustomerTagRepository;
import com.dreambake.repository.OrderRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class CustomerRestController {

    @Autowired
    private CustomerRepository customerRepo;

    @Autowired
    private CustomerTagRepository tagRepo;

    @Autowired
    private OrderRepository orderRepo;

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

    @PostMapping("/customers")
    public Customer createCustomer(@Valid @RequestBody CustomerRequest request) {
        return saveOrUpdateCustomer(new Customer(), request);
    }

    @PutMapping("/customers/{id}")
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
            customerRepo.delete(customer);
            return ResponseEntity.ok().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }

    private Customer saveOrUpdateCustomer(Customer customer, CustomerRequest req) {
        customer.setName(req.name());
        customer.setEmail(req.email());
        customer.setPhone(req.phone());
        customer.setAddress(req.address());

        CustomerTag tag = tagRepo.findByName(req.tagName())
                .orElseGet(() -> tagRepo.findByName("Sin etiqueta").orElse(null));
        customer.setTag(tag);

        return customerRepo.save(customer);
    }

    public record CustomerRequest(
            @NotBlank(message = "El nombre del cliente es obligatorio.") String name,
            String email, String phone, String address, String tagName) {
    }

    // ================= ETIQUETAS (TAGS) =================

    @GetMapping("/tags")
    public List<CustomerTag> getAllTags() {
        return tagRepo.findAll(Sort.by(Sort.Direction.ASC, "displayOrder"));
    }

    @PostMapping("/tags")
    public ResponseEntity<?> createTag(@RequestBody CustomerTag tag) {
        if (tagRepo.findByName(tag.getName()).isPresent()) {
            return ResponseEntity.badRequest().body("La etiqueta ya existe.");
        }
        long count = tagRepo.count();
        tag.setDisplayOrder((int) count);
        return ResponseEntity.ok(tagRepo.save(tag));
    }

    @PutMapping("/tags/{id}")
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
            CustomerTag defaultTag = tagRepo.findByName("Sin etiqueta").orElse(null);
            List<Customer> customers = customerRepo.findByTag(tag);
            for (Customer c : customers) {
                c.setTag(defaultTag);
                customerRepo.save(c);
            }
            tagRepo.delete(tag);
            return ResponseEntity.ok().build();
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/tags/reorder")
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