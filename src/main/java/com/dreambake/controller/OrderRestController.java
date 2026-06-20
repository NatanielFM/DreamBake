package com.dreambake.controller;

import com.dreambake.entity.*;
import com.dreambake.repository.CustomerRepository;
import com.dreambake.repository.CustomerTagRepository;
import com.dreambake.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/orders")
public class OrderRestController {

    @Autowired
    private OrderRepository orderRepo;

    @Autowired
    private CustomerRepository customerRepo;

    @Autowired
    private CustomerTagRepository tagRepo;

    @GetMapping
    public List<Order> getAllOrders() {
        return orderRepo.findAll();
    }

    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestBody Order order) {
        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                item.setOrder(order);
            }
        }
        return ResponseEntity.ok(orderRepo.save(order));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable Long id) {
        if (orderRepo.existsById(id)) {
            orderRepo.deleteById(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    // ================= MAGIA DE NEGOCIO ACTUALIZADA =================

    // Añadimos LocalDate para poder cambiar la fecha al confirmar
    public record ConfirmRequest(Double deposit, LocalDate deliveryDate) {
    }

    @PutMapping("/{id}/confirm")
    public ResponseEntity<?> confirmOrder(@PathVariable Long id, @RequestBody ConfirmRequest req) {
        return orderRepo.findById(id).map(order -> {
            order.setStatus(OrderStatus.CONFIRMED);
            order.setDeposit(req.deposit() != null ? req.deposit() : 0.0);

            if (req.deliveryDate() != null) {
                order.setDeliveryDate(req.deliveryDate());
            }
            orderRepo.save(order);

            if (order.getCustomerPhone() != null && !order.getCustomerPhone().trim().isEmpty()) {
                Optional<Customer> existingCustomer = customerRepo.findByPhone(order.getCustomerPhone());
                if (existingCustomer.isEmpty()) {
                    Customer newCustomer = new Customer();
                    newCustomer.setName(order.getCustomerName());
                    newCustomer.setPhone(order.getCustomerPhone());
                    newCustomer.setEmail(order.getCustomerEmail());

                    CustomerTag tag = tagRepo.findByName("Sin etiqueta").orElse(null);
                    newCustomer.setTag(tag);
                    customerRepo.save(newCustomer);
                }
            }
            return ResponseEntity.ok(order);
        }).orElse(ResponseEntity.notFound().build());
    }

    // Para modificar SÓLO la fecha desde los detalles del pedido
    public record DateUpdateRequest(LocalDate deliveryDate) {
    }

    @PutMapping("/{id}/date")
    public ResponseEntity<?> updateOrderDate(@PathVariable Long id, @RequestBody DateUpdateRequest req) {
        return orderRepo.findById(id).map(order -> {
            order.setDeliveryDate(req.deliveryDate());
            return ResponseEntity.ok(orderRepo.save(order));
        }).orElse(ResponseEntity.notFound().build());
    }

    public record StatusRequest(OrderStatus status) {
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestBody StatusRequest req) {
        return orderRepo.findById(id).map(order -> {
            order.setStatus(req.status());
            return ResponseEntity.ok(orderRepo.save(order));
        }).orElse(ResponseEntity.notFound().build());
    }
}