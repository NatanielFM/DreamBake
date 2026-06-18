package com.dreambake.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders_table")
@Getter
@Setter
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Datos del cliente para este pedido específico
    private String customerName;
    private String customerPhone;
    private String customerEmail;

    // Logística
    private LocalDate deliveryDate;
    private String deliveryShift; // Ej: "10:00 a 12:00" o "19:00 a 20:00"

    // Finanzas
    private Double totalPrice = 0.0;
    private Double deposit = 0.0; // Seña

    @Enumerated(EnumType.STRING)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // Relación: Un pedido tiene muchos ítems
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference // Evita bucles infinitos al convertir a JSON
    private List<OrderItem> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    // Método de conveniencia para vincular los ítems automáticamente
    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }
}