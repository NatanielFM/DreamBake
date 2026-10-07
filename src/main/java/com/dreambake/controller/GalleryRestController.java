package com.dreambake.controller;

import com.dreambake.entity.GalleryImage;
import com.dreambake.repository.GalleryImageRepository;
import com.dreambake.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/gallery")
public class GalleryRestController {

    @Autowired
    private GalleryImageRepository galleryImageRepository;

    @Autowired
    private ProductRepository productRepository;

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

    // Borra una foto de la galería: la fila de la base y el archivo de la carpeta uploads/.
    // Si algún producto la usa como foto, no se borra (el producto quedaría sin imagen).
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteImage(@PathVariable Long id) {
        return galleryImageRepository.findById(id).map(image -> {
            List<String> usedBy = productRepository.findAll().stream()
                    .filter(p -> image.getUrl().equals(p.getImageUrl()))
                    .map(p -> p.getName())
                    .toList();
            if (!usedBy.isEmpty()) {
                return ResponseEntity.status(409).body(Map.of("message",
                        "Esta foto es la imagen de: " + String.join(", ", usedBy) + ". Cambiale la foto a ese producto y después borrala."));
            }

            galleryImageRepository.delete(image);

            // Borrar el archivo. Seguridad: solo dentro de uploads/ (normalize() resuelve los "../")
            try {
                Path uploadDir = Paths.get(UPLOAD_DIR).toAbsolutePath().normalize();
                String fileName = image.getUrl().replaceFirst("^/uploads/", "");
                Path file = uploadDir.resolve(fileName).normalize();
                if (file.startsWith(uploadDir)) {
                    Files.deleteIfExists(file);
                }
            } catch (IOException e) {
                // Si el archivo no se pudo borrar, la foto igual deja de aparecer en el sitio
                System.err.println("No se pudo borrar el archivo de la galería: " + e.getMessage());
            }
            return ResponseEntity.ok().build();
        }).orElse(ResponseEntity.notFound().build());
    }
}
