package com.dreambake.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "gallery_images")
@Getter
@Setter
public class GalleryImage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String url; // La ruta donde se guarda la imagen

    private LocalDateTime uploadedAt = LocalDateTime.now();
}
