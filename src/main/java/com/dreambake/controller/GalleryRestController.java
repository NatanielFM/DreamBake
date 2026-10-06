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

            // Seguridad: solo imágenes. Sin este control se podría subir, por ejemplo, un .html con
            // código que quedaría publicado en /uploads y se ejecutaría en nuestro propio dominio.
            String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
            String extension = original.contains(".") ? original.substring(original.lastIndexOf('.') + 1) : "";
            String contentType = file.getContentType() == null ? "" : file.getContentType();
            if (!List.of("jpg", "jpeg", "png", "webp", "gif").contains(extension) || !contentType.startsWith("image/")) {
                return ResponseEntity.badRequest().body("Solo se permiten imágenes JPG, PNG, WEBP o GIF.");
            }

            // Nombre nuevo y único: no se usa el nombre original (podría traer rutas raras como "../")
            String fileName = UUID.randomUUID() + "." + extension;
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