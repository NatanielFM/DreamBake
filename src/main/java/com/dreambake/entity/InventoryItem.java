package com.dreambake.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Entity
@Table(name = "inventory_items")
@Getter
@Setter
public class InventoryItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Double currentStock;

    // Ya no es obligatorio en BD, por defecto 0.0
    @Column(columnDefinition = "DOUBLE DEFAULT 0.0")
    private Double minimumStock = 0.0;

    @Column(nullable = false)
    private String unit;

    private LocalDate expirationDate;

    @Column(name = "date_added")
    private LocalDate dateAdded;

    // Se ejecuta al crear por primera vez
    @PrePersist
    protected void onCreate() {
        if (dateAdded == null) {
            dateAdded = LocalDate.now();
        }
    }

    // Se ejecuta cada vez que modificas/descuentas stock
    @PreUpdate
    protected void onUpdate() {
        dateAdded = LocalDate.now();
    }
}