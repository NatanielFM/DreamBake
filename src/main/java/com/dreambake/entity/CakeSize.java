package com.dreambake.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "cake_sizes")
public class CakeSize {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Integer price;

    @Column(nullable = false)
    private Integer maxRellenos;

    @Column(nullable = false)
    private Boolean mixSponge = false;

    @Column(nullable = false)
    private Boolean enabled = true;

    public CakeSize() {
    }

    public CakeSize(String name, Integer price, Integer maxRellenos, Boolean mixSponge, Boolean enabled) {
        this.name = name;
        this.price = price;
        this.maxRellenos = maxRellenos;
        this.mixSponge = mixSponge;
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

    public Integer getPrice() {
        return price;
    }

    public void setPrice(Integer price) {
        this.price = price;
    }

    public Integer getMaxRellenos() {
        return maxRellenos;
    }

    public void setMaxRellenos(Integer maxRellenos) {
        this.maxRellenos = maxRellenos;
    }

    public Boolean getMixSponge() {
        return mixSponge;
    }

    public void setMixSponge(Boolean mixSponge) {
        this.mixSponge = mixSponge;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}