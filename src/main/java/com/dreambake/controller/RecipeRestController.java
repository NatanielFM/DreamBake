package com.dreambake.controller;

import com.dreambake.audit.Auditable;
import com.dreambake.entity.AuditAction;
import com.dreambake.entity.AuditModule;
import com.dreambake.service.AuditService;
import com.dreambake.entity.Recipe;
import com.dreambake.entity.RecipeItem;
import com.dreambake.repository.InventoryItemRepository;
import com.dreambake.repository.RecipeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recipes")
public class RecipeRestController {

    @Autowired
    private RecipeRepository recipeRepository;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    // Registro de auditoría (paso 3.3): se usa directo donde hace falta el antes y el después
    @Autowired
    private AuditService audit;

    @GetMapping
    public List<Recipe> getAllRecipes() {
        return recipeRepository.findAll();
    }

    @PostMapping
    @Auditable(action = AuditAction.CREATE, module = AuditModule.RECETAS, entityType = "Receta", entityId = "#body.id",
            description = "Creó la receta «#{#body.name}» (#{#body.items == null ? 0 : #body.items.size()} #{#body.items != null && #body.items.size() == 1 ? 'insumo' : 'insumos'})")
    public ResponseEntity<Recipe> createRecipe(@RequestBody Recipe recipe) {
        if (recipe.getItems() != null) {
            for (RecipeItem item : recipe.getItems()) {
                item.setRecipe(recipe);
                // Ensure inventory item exists
                if (item.getInventoryItem() != null && item.getInventoryItem().getId() != null) {
                    inventoryItemRepository.findById(item.getInventoryItem().getId()).ifPresent(item::setInventoryItem);
                }
            }
        }
        return ResponseEntity.ok(recipeRepository.save(recipe));
    }

    @PutMapping("/{id}")
    @Auditable(action = AuditAction.UPDATE, module = AuditModule.RECETAS, entityType = "Receta", entityId = "#id",
            description = "Editó la receta «#{#body.name}» (#{#body.items.size()} #{#body.items.size() == 1 ? 'insumo' : 'insumos'})")
    public ResponseEntity<Recipe> updateRecipe(@PathVariable Long id, @RequestBody Recipe updatedRecipe) {
        return recipeRepository.findById(id).map(recipe -> {
            recipe.setName(updatedRecipe.getName());
            recipe.setDetails(updatedRecipe.getDetails());
            
            recipe.getItems().clear();
            if (updatedRecipe.getItems() != null) {
                for (RecipeItem item : updatedRecipe.getItems()) {
                    item.setRecipe(recipe);
                    if (item.getInventoryItem() != null && item.getInventoryItem().getId() != null) {
                        inventoryItemRepository.findById(item.getInventoryItem().getId()).ifPresent(item::setInventoryItem);
                    }
                    recipe.getItems().add(item);
                }
            }
            return ResponseEntity.ok(recipeRepository.save(recipe));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRecipe(@PathVariable Long id) {
        java.util.Optional<Recipe> found = recipeRepository.findById(id);
        if (found.isPresent()) {
            String name = found.get().getName();
            recipeRepository.deleteById(id);
            audit.log(AuditAction.DELETE, AuditModule.RECETAS, "Receta", id, "Eliminó la receta «" + name + "»", null);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }
}
