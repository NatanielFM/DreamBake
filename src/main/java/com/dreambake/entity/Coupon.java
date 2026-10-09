package com.dreambake.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "coupons")
@Getter
@Setter
public class Coupon {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponType type;

    @Column(nullable = false)
    private boolean active = true;

    private String description;
    private Integer discountPercentage;
    private String customText;
    private Integer usageLimit;
    private boolean isUnlimited = false;

    // Último día en que se puede usar (incluido). Vacío = no vence. Los cupones de cumpleaños
    // vencen a los 30 días. Lo respetan findUsable (CouponService) y consumeOneUse (CouponRepository).
    private LocalDate expirationDate;
}
