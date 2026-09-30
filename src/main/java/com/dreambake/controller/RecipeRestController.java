package com.dreambake.controller;

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

    @GetMapping
    public List<Recipe> getAllRecipes() {
        return recipeRepository.findAll();
    }

    @PostMapping
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
        if (recipeRepository.existsById(id)) {
            recipeRepository.deleteById(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }
}
