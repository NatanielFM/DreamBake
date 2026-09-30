package com.dreambake.controller;

import com.dreambake.entity.InventoryItem;
import com.dreambake.repository.InventoryItemRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/inventory")
public class InventoryRestController {

    @Autowired
    private InventoryItemRepository inventoryRepo;

    @GetMapping
    public List<InventoryItem> getAllItems() {
        return inventoryRepo.findAll();
    }

    @PostMapping
    public InventoryItem createItem(@Valid @RequestBody InventoryRequest request) {
        return saveOrUpdate(new InventoryItem(), request);
    }

    @PutMapping("/{id}")
    public ResponseEntity<InventoryItem> updateItem(@PathVariable Long id,
            @Valid @RequestBody InventoryRequest request) {
        return inventoryRepo.findById(id)
                .map(item -> ResponseEntity.ok(saveOrUpdate(item, request)))
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteItem(@PathVariable Long id) {
        if (inventoryRepo.existsById(id)) {
            inventoryRepo.deleteById(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    private InventoryItem saveOrUpdate(InventoryItem item, InventoryRequest req) {
        item.setName(req.name());
        item.setCurrentStock(req.currentStock());
        // Si viene null, seteamos 0.0
        item.setMinimumStock(req.minimumStock() != null ? req.minimumStock() : 0.0);
        item.setUnit(req.unit());
        item.setExpirationDate(req.expirationDate());
        return inventoryRepo.save(item);
    }

    @Autowired
    private com.dreambake.repository.OrderRepository orderRepo;

    @Autowired
    private com.dreambake.repository.ProductRepository productRepo;

    @PostMapping("/test-deduct/{orderId}")
    public ResponseEntity<String> testDeductInventory(@PathVariable Long orderId) {
        System.out.println("--- TEST DEDUCTION START ---");
        System.out.println("Order ID: " + orderId);
        
        com.dreambake.entity.Order order = orderRepo.findById(orderId).orElse(null);
        if (order == null) {
            return ResponseEntity.badRequest().body("Failed: Order not found");
        }
        
        if (order.getItems() == null || order.getItems().isEmpty()) {
            return ResponseEntity.ok("Deduction complete: No items in order");
        }
        
        java.util.Map<Long, Double> requiredStock = new java.util.HashMap<>();
        for (com.dreambake.entity.OrderItem item : order.getItems()) {
            if (item.getProductType() != null) {
                productRepo.findByNameIgnoreCase(item.getProductType()).ifPresent(product -> {
                    if (product.getRecipe() != null && product.getRecipe().getItems() != null) {
                        for (com.dreambake.entity.RecipeItem recipeItem : product.getRecipe().getItems()) {
                            if (recipeItem.getInventoryItem() != null && recipeItem.getInventoryItem().getId() != null) {
                                Long invId = recipeItem.getInventoryItem().getId();
                                requiredStock.put(invId, requiredStock.getOrDefault(invId, 0.0) + recipeItem.getQuantityRequired());
                            }
                        }
                    }
                });
            }
        }
        
        for (java.util.Map.Entry<Long, Double> entry : requiredStock.entrySet()) {
            InventoryItem invItem = inventoryRepo.findById(entry.getKey()).orElse(null);
            if (invItem != null) {
                double current = invItem.getCurrentStock() != null ? invItem.getCurrentStock() : 0.0;
                double required = entry.getValue();
                
                System.out.println("Ingredient: " + invItem.getName());
                System.out.println("Stock Before: " + current);
                System.out.println("Amount to Deduct: " + required);
                
                double newStock = current - required;
                if (newStock < 0) {
                    newStock = 0.0;
                }
                
                System.out.println("Stock After: " + newStock);
                invItem.setCurrentStock(newStock);
                inventoryRepo.save(invItem);
            }
        }
        
        return ResponseEntity.ok("Deduction complete");
    }

    public record InventoryRequest(
            @NotBlank(message = "El nombre del insumo es obligatorio.") String name,
            @NotNull(message = "Debes asignar el stock actual.") @PositiveOrZero(message = "El stock no puede ser negativo.") Double currentStock,
            @PositiveOrZero(message = "El stock mínimo no puede ser negativo.") Double minimumStock,
            @NotBlank(message = "La unidad de medida es obligatoria.") String unit,
            LocalDate expirationDate) {
    }
}