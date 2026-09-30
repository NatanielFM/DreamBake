package com.dreambake.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonBackReference;

@Entity
@Table(name = "order_items")
@Getter
@Setter
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "order_id", nullable = false)
    @JsonBackReference // Evita bucles infinitos al convertir a JSON
    private Order order;

    // Ej: "Torta Personalizada", "Docena de Alfajores"
    private String productType;

    // Relación opcional con Producto estándar (nulo si es torta personalizada)
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = true)
    private Product product;

    // Indica si este ítem representa una Torta Personalizada
    @Column(name = "is_custom_cake")
    private Boolean isCustomCake = false;

    // Aquí guardaremos: "20 porciones, Fondant, Relleno de Mousse, Temática Bluey"
    @Column(columnDefinition = "TEXT")
    private String details;

    private Double price;
}