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

    @Column(nullable = false)
    private Double minimumStock;

    @Column(nullable = false)
    private String unit; // Ej: "Kg", "Litros", "Unidades", "Gramos"

    // Puede ser null para cosas que no vencen (ej: Cajas de cartón)
    private LocalDate expirationDate;
}