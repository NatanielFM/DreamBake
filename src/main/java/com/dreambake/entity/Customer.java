package com.dreambake.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Entity
@Table(name = "customers")
@Getter
@Setter
public class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String email;
    private String phone;
    private String address;

    private LocalDate birthDate;
    private Integer totalPurchases = 0;

    @Column(name = "date_added")
    private LocalDate dateAdded;

    @ManyToOne
    @JoinColumn(name = "tag_id")
    private CustomerTag tag;

    // Resumen de sus pedidos. No son columnas (@Transient): el servidor los calcula
    // cada vez que se pide la lista de clientes (ver CustomerRestController).
    @Transient
    private Integer ordersCount;
    @Transient
    private Double totalSpent;

    @PrePersist
    protected void onCreate() {
        if (dateAdded == null) {
            dateAdded = LocalDate.now();
        }
    }
}
