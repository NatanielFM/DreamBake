package com.dreambake.controller;

import com.dreambake.entity.*;
import com.dreambake.repository.CustomerRepository;
import com.dreambake.repository.CustomerTagRepository;
import com.dreambake.repository.OrderRepository;
import com.dreambake.repository.ProductRepository;
import com.dreambake.repository.InventoryItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

@RestController
@RequestMapping("/api/orders")
public class OrderRestController {

    @Autowired
    private OrderRepository orderRepo;

    @Autowired
    private CustomerRepository customerRepo;

    @Autowired
    private CustomerTagRepository tagRepo;

    @Autowired
    private ProductRepository productRepo;

    @Autowired
    private InventoryItemRepository inventoryItemRepo;

    @GetMapping
    public List<Order> getAllOrders() {
        List<Order> orders = orderRepo.findAll();
        orders.removeIf(o -> o.getStatus() == OrderStatus.REJECTED);
        for (Order order : orders) {
            if (order.getStatus() == OrderStatus.PENDING) {
                calculateStockStatus(order);
            }
        }
        return orders;
    }

    @GetMapping("/stats/monthly")
    public ResponseEntity<Map<String, Object>> getMonthlyStats(@RequestParam(required = false) Integer year, @RequestParam(required = false) Integer month) {
        int y = year != null ? year : LocalDate.now().getYear();
        int m = month != null ? month : LocalDate.now().getMonthValue();
        
        LocalDate start = LocalDate.of(y, m, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        
        List<Order> orders = orderRepo.findAll().stream()
            .filter(o -> (o.getStatus() == OrderStatus.CONFIRMED || o.getStatus() == OrderStatus.COMPLETED)
                && o.getDeliveryDate() != null
                && !o.getDeliveryDate().isBefore(start)
                && !o.getDeliveryDate().isAfter(end))
            .toList();

        double total = orders.stream().mapToDouble(Order::getTotalPrice).sum();
        long count = orders.size();

        return ResponseEntity.ok(Map.of("revenue", total, "ordersCount", count));
    }

    @GetMapping("/stats/top-products")
    public ResponseEntity<List<Map<String, Object>>> getTopProducts() {
        List<Object[]> results = orderRepo.findTopProductsAggregation(List.of(OrderStatus.CONFIRMED, OrderStatus.COMPLETED));
        List<Map<String, Object>> list = new ArrayList<>();
        for (Object[] row : results) {
            list.add(Map.of("name", row[0], "count", row[1]));
        }
        return ResponseEntity.ok(list);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Order> createOrder(@RequestBody Order order) {
        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                item.setOrder(order);
            }
        }
        calculateStockStatus(order);
        Order savedOrder = orderRepo.save(order);
        if (savedOrder.getStatus() == OrderStatus.CONFIRMED) {
            deductInventory(savedOrder);
        }
        return ResponseEntity.ok(savedOrder);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable Long id) {
        if (orderRepo.existsById(id)) {
            orderRepo.deleteById(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    // ================= EDICIÓN Y CONFIRMACIÓN =================

    public record ConfirmRequest(Double deposit, LocalDate deliveryDate, Boolean force) {
    }

    @PutMapping("/{id}/confirm")
    @Transactional
    public ResponseEntity<?> confirmOrder(@PathVariable Long id, @RequestBody ConfirmRequest req) {
        return orderRepo.findById(id).map(order -> {
            boolean force = req.force() != null ? req.force() : false;
            
            if (order.getStatus() != OrderStatus.CONFIRMED) {
                ResponseEntity<?> errorResponse = checkStockForConfirmation(order, force);
                if (errorResponse != null) {
                    return errorResponse;
                }
                deductInventory(order);
            }

            order.setStatus(OrderStatus.CONFIRMED);
            order.setDeposit(req.deposit() != null ? req.deposit() : 0.0);

            if (req.deliveryDate() != null) {
                order.setDeliveryDate(req.deliveryDate());
            }

            // Auto-crear cliente
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
            return ResponseEntity.ok(orderRepo.save(order));
        }).orElse(ResponseEntity.notFound().build());
    }

    // EDICIÓN COMPLETA DEL PEDIDO (Desde el botón "Editar" del Modal)
    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> updateOrderFull(@PathVariable Long id, @RequestBody Order updatedOrder) {
        return orderRepo.findById(id).map(order -> {
            order.setCustomerName(updatedOrder.getCustomerName());
            order.setCustomerPhone(updatedOrder.getCustomerPhone());
            order.setCustomerEmail(updatedOrder.getCustomerEmail());
            order.setDeliveryDate(updatedOrder.getDeliveryDate());
            order.setTotalPrice(updatedOrder.getTotalPrice());
            order.setDeposit(updatedOrder.getDeposit());

            // Si el estado viene en la petición, lo actualizamos
            if (updatedOrder.getStatus() != null) {
                if (updatedOrder.getStatus() == OrderStatus.CONFIRMED && order.getStatus() == OrderStatus.PENDING) {
                    deductInventory(order);
                }
                order.setStatus(updatedOrder.getStatus());
            }

            // Limpiamos los ítems viejos y guardamos el nuevo texto "aplanado"
            order.getItems().clear();
            if (updatedOrder.getItems() != null) {
                for (OrderItem item : updatedOrder.getItems()) {
                    item.setOrder(order);
                    order.getItems().add(item);
                }
            }
            calculateStockStatus(order);
            return ResponseEntity.ok(orderRepo.save(order));
        }).orElse(ResponseEntity.notFound().build());
    }

    public record StatusRequest(OrderStatus status, Boolean force) {
    }

    @PutMapping("/{id}/status")
    @Transactional
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestBody StatusRequest req) {
        return orderRepo.findById(id).map(order -> {
            boolean force = req.force() != null ? req.force() : false;
            if (req.status() == OrderStatus.CONFIRMED && order.getStatus() == OrderStatus.PENDING) {
                ResponseEntity<?> errorResponse = checkStockForConfirmation(order, force);
                if (errorResponse != null) {
                    return errorResponse;
                }
                deductInventory(order);
            }
            order.setStatus(req.status());
            return ResponseEntity.ok(orderRepo.save(order));
        }).orElse(ResponseEntity.notFound().build());
    }

    private Map<Long, Double> getRequiredStock(Order order) {
        Map<Long, Double> requiredStock = new HashMap<>();
        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                Product product = item.getProduct();
                if (product == null && item.getProductType() != null) {
                    product = productRepo.findByNameIgnoreCase(item.getProductType()).orElse(null);
                }
                
                if (product != null && product.getRecipe() != null && product.getRecipe().getItems() != null) {
                    for (RecipeItem recipeItem : product.getRecipe().getItems()) {
                        if (recipeItem.getInventoryItem() != null && recipeItem.getInventoryItem().getId() != null) {
                            Long invId = recipeItem.getInventoryItem().getId();
                            Double qty = recipeItem.getQuantityRequired();
                            requiredStock.put(invId, requiredStock.getOrDefault(invId, 0.0) + qty);
                        }
                    }
                }
            }
        }
        return requiredStock;
    }

    private ResponseEntity<?> checkStockForConfirmation(Order order, boolean force) {
        Map<Long, Double> requiredStock = getRequiredStock(order);
        boolean insufficient = false;
        
        for (Map.Entry<Long, Double> entry : requiredStock.entrySet()) {
            InventoryItem invItem = inventoryItemRepo.findById(entry.getKey()).orElse(null);
            if (invItem != null) {
                boolean isExpired = invItem.getExpirationDate() != null && invItem.getExpirationDate().isBefore(LocalDate.now());
                double currentValid = (invItem.getCurrentStock() != null && !isExpired) ? invItem.getCurrentStock() : 0.0;
                double required = entry.getValue();
                
                if (currentValid < required) {
                    insufficient = true;
                    break;
                }
            } else {
                insufficient = true;
                break;
            }
        }
        
        if (insufficient && !force) {
            return ResponseEntity.badRequest().body(Map.of("error", "INSUFFICIENT_STOCK", "message", "Stock insuficiente o insumos vencidos para completar el pedido. ¿Desea forzar la confirmación?"));
        }
        
        return null;
    }

    private void calculateStockStatus(Order order) {
        if (order.getItems() == null || order.getItems().isEmpty()) {
            order.setStockStatus("Realizable");
            return;
        }

        Map<Long, Double> requiredStock = getRequiredStock(order);

        boolean hasStockIssues = false;
        boolean isSinInventario = false;

        System.out.println("--- TEST VALIDATION START ---");

        for (Map.Entry<Long, Double> entry : requiredStock.entrySet()) {
            InventoryItem invItem = inventoryItemRepo.findById(entry.getKey()).orElse(null);
            if (invItem != null) {
                boolean isExpired = invItem.getExpirationDate() != null && invItem.getExpirationDate().isBefore(LocalDate.now());
                double currentValid = (invItem.getCurrentStock() != null && !isExpired) ? invItem.getCurrentStock() : 0.0;
                double min = invItem.getMinimumStock() != null ? invItem.getMinimumStock() : 0.0;
                double required = entry.getValue();

                System.out.println("Product: " + invItem.getName());
                System.out.println("Required Amount: " + required);
                System.out.println("Actual Valid Stock: " + currentValid);

                if (currentValid < required) {
                    isSinInventario = true;
                    break;
                } else if ((currentValid - required) < min) {
                    hasStockIssues = true;
                }
            } else {
                isSinInventario = true;
                break;
            }
        }

        if (isSinInventario) {
            order.setStockStatus("Sin inventario");
        } else if (hasStockIssues) {
            order.setStockStatus("Bajo stock");
        } else {
            order.setStockStatus("Realizable");
        }
        System.out.println("Calculated Status: " + order.getStockStatus());
    }

    private void deductInventory(Order order) {
        if (order.getItems() == null || order.getItems().isEmpty()) {
            return;
        }

        Map<Long, Double> requiredStock = getRequiredStock(order);
        List<InventoryItem> itemsToUpdate = new ArrayList<>();

        for (Map.Entry<Long, Double> entry : requiredStock.entrySet()) {
            inventoryItemRepo.findById(entry.getKey()).ifPresent(invItem -> {
                double current = invItem.getCurrentStock() != null ? invItem.getCurrentStock() : 0.0;
                double required = entry.getValue();
                double newStock = current - required;
                
                if (newStock < 0) {
                    newStock = 0.0;
                }
                
                invItem.setCurrentStock(newStock);
                itemsToUpdate.add(invItem);
            });
        }
        
        inventoryItemRepo.saveAll(itemsToUpdate);
    }
}
