package com.dreambake.controller;

import com.dreambake.audit.Auditable;
import com.dreambake.audit.AuditText;
import com.dreambake.entity.AuditAction;
import com.dreambake.entity.AuditModule;
import com.dreambake.service.AuditService;
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
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;

@RestController
@RequestMapping("/api/inventory")
public class InventoryRestController {

    @Autowired
    private InventoryItemRepository inventoryRepo;

    // Registro de auditoría (paso 3.3): se usa directo donde hace falta el antes y el después
    @Autowired
    private AuditService audit;

    @GetMapping
    public List<InventoryItem> getAllItems() {
        return inventoryRepo.findAll();
    }

    @PostMapping
    @Auditable(action = AuditAction.CREATE, module = AuditModule.INVENTARIO, entityType = "Insumo", entityId = "#result.id",
            description = "Agregó el insumo «#{#result.name}» con #{T(com.dreambake.audit.AuditText).number(#result.currentStock)} #{#result.unit}")
    public InventoryItem createItem(@Valid @RequestBody InventoryRequest request) {
        return saveOrUpdate(new InventoryItem(), request);
    }

    @PutMapping("/{id}")
    public ResponseEntity<InventoryItem> updateItem(@PathVariable Long id,
            @Valid @RequestBody InventoryRequest request) {
        return inventoryRepo.findById(id)
                .map(item -> {
                    // Ajuste de stock: se guarda la cantidad anterior y la nueva (llamada directa)
                    Map<String, Object> before = snapshot(item);
                    InventoryItem saved = saveOrUpdate(item, request);
                    Map<String, Object> diff = AuditText.diff(before, snapshot(saved));
                    String text = Objects.equals(before.get("stock"), saved.getCurrentStock())
                            ? "Editó el insumo «" + saved.getName() + "»" + AuditText.changedFields(diff)
                            : "Ajustó el stock de «" + saved.getName() + "»: de " + AuditText.number((Number) before.get("stock")) + " a "
                                    + AuditText.number(saved.getCurrentStock()) + " " + saved.getUnit();
                    audit.log(AuditAction.UPDATE, AuditModule.INVENTARIO, "Insumo", saved.getId(), text, diff);
                    return ResponseEntity.ok(saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteItem(@PathVariable Long id) {
        Optional<InventoryItem> found = inventoryRepo.findById(id);
        if (found.isPresent()) {
            Map<String, Object> before = snapshot(found.get());
            inventoryRepo.deleteById(id);
            audit.log(AuditAction.DELETE, AuditModule.INVENTARIO, "Insumo", id,
                    "Eliminó el insumo «" + found.get().getName() + "»", AuditText.change(before, null));
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    // Los datos de un insumo que se comparan en la auditoría
    private Map<String, Object> snapshot(InventoryItem i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nombre", i.getName());
        m.put("stock", i.getCurrentStock());
        m.put("minimo", i.getMinimumStock());
        m.put("unidad", i.getUnit());
        m.put("vencimiento", i.getExpirationDate() == null ? null : i.getExpirationDate().toString());
        m.put("costo", i.getUnitCost());
        return m;
    }

    private InventoryItem saveOrUpdate(InventoryItem item, InventoryRequest req) {
        item.setName(req.name());
        item.setCurrentStock(req.currentStock());
        // Si viene null, seteamos 0.0
        item.setMinimumStock(req.minimumStock() != null ? req.minimumStock() : 0.0);
        item.setUnit(req.unit());
        item.setExpirationDate(req.expirationDate());
        // El costo es un monto: solo el admin lo cambia. Si edita un pastelero (stock, vencimiento), se conserva el que había.
        if (!com.dreambake.util.MoneyVisibility.hiddenForCurrentUser()) {
            item.setUnitCost(req.unitCost());
        }
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