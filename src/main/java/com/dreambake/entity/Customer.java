package com.dreambake.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

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

    // ===== Consentimiento para emails promocionales (Ley 25.326) =====
    // true solo si el propio cliente lo aceptó en el checkout. El administrador puede
    // desactivarlo, pero nunca activarlo por el cliente.
    private Boolean marketingConsent = false;

    // Cuándo lo aceptó (queda como constancia)
    private LocalDateTime consentDate;

    // Código secreto del link "darse de baja" de los emails (/baja?token=...).
    // Se genera solo y nunca se manda en las respuestas JSON (@JsonIgnore).
    @JsonIgnore
    @Column(unique = true, length = 36)
    private String unsubscribeToken;

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
        if (unsubscribeToken == null) {
            unsubscribeToken = UUID.randomUUID().toString();
        }
        if (marketingConsent == null) {
            marketingConsent = false;
        }
    }
}
