package com.dreambake.controller;

import com.dreambake.entity.GalleryImage;
import com.dreambake.repository.GalleryImageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/gallery")
public class GalleryRestController {

    @Autowired
    private GalleryImageRepository galleryImageRepository;

    private final String UPLOAD_DIR = "uploads/";

    @GetMapping
    public List<GalleryImage> getAllImages() {
        return galleryImageRepository.findAll();
    }

    @PostMapping
    public ResponseEntity<?> uploadImage(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Archivo vacío");
        }
        try {
            // Crear carpeta si no existe
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // Generar nombre único para evitar que se sobrescriban imágenes con el mismo
            // nombre
            String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
            Path filePath = uploadPath.resolve(fileName);
            Files.copy(file.getInputStream(), filePath);

            // Guardar en BD
            GalleryImage image = new GalleryImage();
            image.setUrl("/uploads/" + fileName);
            galleryImageRepository.save(image);

            return ResponseEntity.ok(image);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Error al subir el archivo: " + e.getMessage());
        }
    }
}