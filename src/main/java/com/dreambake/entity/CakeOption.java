package com.dreambake.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "cake_options")
public class CakeOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String category; // "Sponge", "Crema", "Fondant"

    @Column(nullable = false)
    private Boolean enabled = true;

    public CakeOption() {
    }

    public CakeOption(String name, String category, Boolean enabled) {
        this.name = name;
        this.category = category;
        this.enabled = enabled;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}