package com.dreambake.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "customer_tags")
@Getter
@Setter
public class CustomerTag {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private boolean isDeletable = true;

    @Column(nullable = false, columnDefinition = "int default 0")
    private Integer displayOrder = 0;
}
