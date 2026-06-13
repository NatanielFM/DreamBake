package com.dreambake.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Entity
@Table(name = "inventory_items")
@Getter @Setter
public class InventoryItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Double currentStock;

    @Column(nullable = false)
    private Double minimumStock;

    @Column(nullable = false)
    private String unit;

    private LocalDate expirationDate;

    // Nuevo campo: Fecha en que se cargó
    @Column(name = "date_added")
    private LocalDate dateAdded;

    @PrePersist
    protected void onCreate() {
        if (dateAdded == null) {
            dateAdded = LocalDate.now();
        }
    }
}