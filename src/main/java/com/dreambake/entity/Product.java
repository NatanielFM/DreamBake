package com.dreambake.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Getter
@Setter
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private Double price;

    @Column(nullable = false)
    private boolean enabled = true;

    // Relación Muchos a Uno: Muchos productos pueden tener la misma categoría
    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    // Relación Muchos a Uno: Varios productos podrían usar la misma foto
    @ManyToOne
    @JoinColumn(name = "image_id")
    private GalleryImage image;
}
