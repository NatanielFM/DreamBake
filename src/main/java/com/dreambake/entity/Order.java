package com.dreambake.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
    private String deliveryShift; // Ej: "Por la mañana" o "Por la tarde"

    // Cómo se entrega: "Retiro" (lo busca el cliente) o "Envío" (con la dirección de abajo)
    private String deliveryMethod;
    private String deliveryAddress;

    // Código del cupón usado en la tienda (el servidor ya lo validó y le descontó un uso)
    private String couponCode;

    // Datos opcionales del checkout para la fidelización. Se guardan en el pedido porque el
    // Cliente recién se crea al confirmarlo; ahí se copian a su ficha (ver linkCustomer).
    private LocalDate customerBirthDate;
    // ¿Tildó "Quiero recibir promociones y un regalo en mi cumpleaños"? Y cuándo (constancia).
    private Boolean marketingConsent = false;
    private LocalDateTime marketingConsentAt;

    // Cliente al que pertenece el pedido. Se completa al confirmarlo (se busca por teléfono
    // o se crea uno nuevo). Los pedidos nuevos de la tienda todavía no lo tienen.
    @ManyToOne
    @JoinColumn(name = "customer_id")
    @JsonIgnoreProperties({ "tag", "address", "birthDate", "totalPurchases", "dateAdded", "ordersCount", "totalSpent",
            "marketingConsent", "consentDate", "unsubscribeToken" })
    private Customer customer;

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

    @Transient
    private String stockStatus;

    // Código de seguimiento para el cliente (paso 6.5), por ejemplo DB-7K3P9Q: corto para dictarlo por
    // WhatsApp y al azar para que no se pueda adivinar (no es el número de pedido, que es correlativo).
    // updatable = false: una vez creado no cambia nunca (ni aunque el panel mande el pedido sin él).
    @Column(name = "tracking_code", unique = true, length = 12, updatable = false)
    private String trackingCode;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (trackingCode == null) {
            trackingCode = TrackingCodes.generate();
        }
    }

    // Método de conveniencia para vincular los ítems automáticamente
    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }
}