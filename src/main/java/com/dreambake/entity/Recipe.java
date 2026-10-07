package com.dreambake.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "recipes")
@Getter
@Setter
public class Recipe {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String details;

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<RecipeItem> items = new ArrayList<>();

    public void addItem(RecipeItem item) {
        items.add(item);
        item.setRecipe(this);
    }

    // ===== Costo estimado (no se guarda: se calcula con el costo actual de cada insumo) =====
    // Jackson convierte estos métodos en los campos "estimatedCost" y "costComplete" del JSON.

    // Suma de (cantidad que lleva × costo por unidad del insumo)
    public double getEstimatedCost() {
        double total = 0;
        for (RecipeItem item : items) {
            InventoryItem insumo = item.getInventoryItem();
            if (insumo != null && insumo.getUnitCost() != null && item.getQuantityRequired() != null) {
                total += item.getQuantityRequired() * insumo.getUnitCost();
            }
        }
        return Math.round(total * 100) / 100.0; // redondeado a centavos
    }

    // true si TODOS los insumos tienen costo cargado (si falta alguno, el costo es parcial)
    public boolean isCostComplete() {
        if (items.isEmpty()) return false;
        for (RecipeItem item : items) {
            InventoryItem insumo = item.getInventoryItem();
            if (insumo == null || insumo.getUnitCost() == null) return false;
        }
        return true;
    }
}
