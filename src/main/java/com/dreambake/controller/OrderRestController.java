package com.dreambake.controller;

import com.dreambake.entity.*;
import com.dreambake.util.InputSanitizer;
import com.dreambake.repository.CustomerRepository;
import com.dreambake.repository.CustomerTagRepository;
import com.dreambake.repository.OrderRepository;
import com.dreambake.repository.ProductRepository;
import com.dreambake.repository.InventoryItemRepository;
import com.dreambake.service.OrderPricingService;
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

    @Autowired
    private OrderPricingService pricingService;

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
    public ResponseEntity<?> createOrder(@RequestBody Order order, org.springframework.security.core.Authentication auth) {
        // ¿Lo crea el personal desde el panel o un cliente desde la tienda pública?
        boolean isStaff = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ADMIN") || a.getAuthority().equals("PASTELERO"));

        // Siempre es un pedido NUEVO: si viniera un id, save() pisaría un pedido existente
        order.setId(null);
        order.setCustomer(null); // el cliente se une al confirmar (ver linkCustomer)
        if (order.getItems() != null) {
            order.getItems().forEach(item -> item.setId(null));
        }

        // Retiro o envío (y la dirección, que es obligatoria si es envío)
        String deliveryError = normalizeDelivery(order);

        if (!isStaff) {
            // Pedido desde la tienda: el cliente no decide el estado, la seña ni la fecha de creación
            order.setStatus(OrderStatus.PENDING);
            order.setDeposit(0.0);
            order.setCreatedAt(null);

            // Datos obligatorios
            if (order.getCustomerName() == null || order.getCustomerName().isBlank()
                    || order.getCustomerPhone() == null || order.getCustomerPhone().isBlank()
                    || order.getDeliveryDate() == null
                    || order.getItems() == null || order.getItems().isEmpty() || order.getItems().size() > 100) {
                return ResponseEntity.badRequest().body(Map.of("message", "Faltan datos del pedido."));
            }
            if (deliveryError != null) {
                return ResponseEntity.badRequest().body(Map.of("message", deliveryError));
            }

            // Precios reales y cupón: los calcula el servidor (no se usa el total que manda el navegador)
            String priceError = pricingService.priceStoreOrder(order);
            if (priceError != null) {
                return ResponseEntity.badRequest().body(Map.of("message", priceError));
            }

            // Textos limpios (evita que se inyecte código en el panel) y con largo máximo
            order.setCustomerName(InputSanitizer.clean(order.getCustomerName(), 120));
            order.setCustomerPhone(InputSanitizer.clean(order.getCustomerPhone(), 40));
            order.setCustomerEmail(InputSanitizer.clean(order.getCustomerEmail(), 150));
            order.setDeliveryShift(InputSanitizer.clean(order.getDeliveryShift(), 60));
            order.setDeliveryAddress(InputSanitizer.clean(order.getDeliveryAddress(), 250));
            for (OrderItem item : order.getItems()) {
                item.setProductType(InputSanitizer.clean(item.getProductType(), 200));
                item.setDetails(InputSanitizer.clean(item.getDetails(), 3000));
            }
        } else if (deliveryError != null) {
            return ResponseEntity.badRequest().body(Map.of("message", deliveryError));
        }

        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                item.setOrder(order);
            }
        }
        // Un pedido cargado a mano como agendado o entregado ya tiene cliente
        if (order.getStatus() == OrderStatus.CONFIRMED || order.getStatus() == OrderStatus.COMPLETED) {
            linkCustomer(order);
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

            // Une el pedido con su cliente (lo crea si su teléfono no existe)
            linkCustomer(order);
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
            order.setDeliveryDate(updatedOrder.getDeliveryDate());
            order.setTotalPrice(updatedOrder.getTotalPrice());
            order.setDeposit(updatedOrder.getDeposit());

            // Estos datos solo se cambian si vienen en la petición
            // (antes el email se borraba al editar porque el panel no lo mandaba)
            if (updatedOrder.getCustomerEmail() != null) order.setCustomerEmail(updatedOrder.getCustomerEmail());
            if (updatedOrder.getDeliveryShift() != null) order.setDeliveryShift(updatedOrder.getDeliveryShift());
            if (updatedOrder.getDeliveryMethod() != null) {
                order.setDeliveryMethod(updatedOrder.getDeliveryMethod());
                order.setDeliveryAddress(updatedOrder.getDeliveryAddress());
                String deliveryError = normalizeDelivery(order);
                if (deliveryError != null) {
                    return ResponseEntity.badRequest().body(Map.of("message", deliveryError));
                }
            }

            // Si el estado viene en la petición, lo actualizamos
            if (updatedOrder.getStatus() != null) {
                if (updatedOrder.getStatus() == OrderStatus.CONFIRMED && order.getStatus() == OrderStatus.PENDING) {
                    deductInventory(order);
                    linkCustomer(order);
                }
                order.setStatus(updatedOrder.getStatus());
            }

            // Los productos solo se reemplazan si el panel los manda (si no se tocó el texto
            // de los productos, se conservan los originales con su precio y su receta)
            if (updatedOrder.getItems() != null) {
                order.getItems().clear();
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
            if (req.status() == OrderStatus.CONFIRMED || req.status() == OrderStatus.COMPLETED) {
                linkCustomer(order);
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

        for (Map.Entry<Long, Double> entry : requiredStock.entrySet()) {
            InventoryItem invItem = inventoryItemRepo.findById(entry.getKey()).orElse(null);
            if (invItem != null) {
                boolean isExpired = invItem.getExpirationDate() != null && invItem.getExpirationDate().isBefore(LocalDate.now());
                double currentValid = (invItem.getCurrentStock() != null && !isExpired) ? invItem.getCurrentStock() : 0.0;
                double min = invItem.getMinimumStock() != null ? invItem.getMinimumStock() : 0.0;
                double required = entry.getValue();

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

    // ================= CLIENTE Y ENTREGA =================

    // Une el pedido con su cliente. Si no hay ninguno con ese teléfono, lo crea con
    // la etiqueta "Sin etiqueta" (así la dueña no tiene que cargar a mano a cada cliente).
    private void linkCustomer(Order order) {
        if (order.getCustomer() != null) return;
        String phone = order.getCustomerPhone() == null ? "" : order.getCustomerPhone().trim();
        if (phone.isEmpty()) return;

        Customer customer = customerRepo.findFirstByPhone(phone).orElseGet(() -> {
            Customer newCustomer = new Customer();
            newCustomer.setName(order.getCustomerName() != null ? order.getCustomerName() : "Cliente");
            newCustomer.setPhone(phone);
            newCustomer.setEmail(order.getCustomerEmail());
            newCustomer.setTag(tagRepo.findByName("Sin etiqueta").orElse(null));
            return newCustomer;
        });
        // Si pidió envío y el cliente todavía no tenía dirección, se guarda la del pedido
        if ((customer.getAddress() == null || customer.getAddress().isBlank())
                && "Envío".equals(order.getDeliveryMethod()) && order.getDeliveryAddress() != null) {
            customer.setAddress(order.getDeliveryAddress());
        }
        order.setCustomer(customerRepo.save(customer));
    }

    // Deja el método de entrega en uno de los dos valores válidos ("Retiro" o "Envío").
    // Devuelve un mensaje de error si es envío y falta la dirección; si no, null.
    private String normalizeDelivery(Order order) {
        String method = order.getDeliveryMethod() == null ? "" : order.getDeliveryMethod().trim().toLowerCase();
        if (method.isEmpty()) {
            order.setDeliveryMethod(null);   // pedidos viejos o cargados sin este dato
            order.setDeliveryAddress(null);
            return null;
        }
        if (method.startsWith("env")) {
            order.setDeliveryMethod("Envío");
            if (order.getDeliveryAddress() == null || order.getDeliveryAddress().isBlank()) {
                return "Escribí la dirección para el envío.";
            }
            String address = order.getDeliveryAddress().trim();
            // La columna de la base admite 255 caracteres
            order.setDeliveryAddress(address.length() > 250 ? address.substring(0, 250) : address);
        } else {
            order.setDeliveryMethod("Retiro");
            order.setDeliveryAddress(null);
        }
        return null;
    }
}
