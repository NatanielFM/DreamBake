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
        item.setUnitCost(req.unitCost());
        return inventoryRepo.save(item);
    }

    public record InventoryRequest(
            @NotBlank(message = "El nombre del insumo es obligatorio.") String name,
            @NotNull(message = "Debes asignar el stock actual.") @PositiveOrZero(message = "El stock no puede ser negativo.") Double currentStock,
            @PositiveOrZero(message = "El stock mínimo no puede ser negativo.") Double minimumStock,
            @NotBlank(message = "La unidad de medida es obligatoria.") String unit,
            LocalDate expirationDate,
            @PositiveOrZero(message = "El costo no puede ser negativo.") Double unitCost) {
    }
}