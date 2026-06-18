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

    // Aquí guardaremos: "20 porciones, Fondant, Relleno de Mousse, Temática Bluey"
    @Column(columnDefinition = "TEXT")
    private String details;

    private Double price;
}