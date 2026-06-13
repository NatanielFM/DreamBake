package com.dreambake.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "categories")
@Getter
@Setter
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private boolean isDeletable = true;

    // Columna para saber en qué posición va (1°, 2°, 3°...)
    @Column(nullable = false, columnDefinition = "int default 0")
    private Integer displayOrder = 0;
}