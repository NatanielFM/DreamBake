package com.dreambake.controller;

import com.dreambake.audit.Auditable;
import com.dreambake.entity.AuditAction;
import com.dreambake.entity.AuditModule;
import com.dreambake.entity.GalleryImage;
import com.dreambake.repository.GalleryImageRepository;
import com.dreambake.repository.ProductRepository;
import com.dreambake.service.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/gallery")
public class GalleryRestController {

    @Autowired
    private GalleryImageRepository galleryImageRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private FileStorageService fileStorage;

    private static final Logger log = LoggerFactory.getLogger(GalleryRestController.class);

    @GetMapping
    public List<GalleryImage> getAllImages() {
        return galleryImageRepository.findAll();
    }

    @PostMapping
    @Auditable(action = AuditAction.CREATE, module = AuditModule.GALERIA, entityType = "Foto", entityId = "#body.id",
            description = "Subió una foto a la galería (#{#file.originalFilename})")
    public ResponseEntity<?> uploadImage(@RequestParam("file") MultipartFile file) {
        try {
            // Validación (contenido real, 5 MB), achicado a 1600 px y nombre UUID: FileStorageService (paso 6.7)
            FileStorageService.Stored stored = fileStorage.storeImage(file);
            GalleryImage image = new GalleryImage();
            image.setUrl(stored.url());
            galleryImageRepository.save(image);
            return ResponseEntity.ok(image);
        } catch (FileStorageService.UploadException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IOException e) {
            log.error("No se pudo guardar la foto subida: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("message", "No se pudo guardar la foto en el servidor. Probá de nuevo."));
        }
    }

    // Borra una foto de la galería: la fila de la base y, si ya nadie la usa, el archivo de uploads/.
    // Si algún producto la usa como foto, no se borra (el producto quedaría sin imagen).
    @DeleteMapping("/{id}")
    @Auditable(action = AuditAction.DELETE, module = AuditModule.GALERIA, entityType = "Foto", entityId = "#id",
            description = "Borró una foto de la galería (foto #{#id})")
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
            // El archivo se borra solo si no lo usa nadie más (por ejemplo, una campaña guardada)
            fileStorage.deleteIfUnused(image.getUrl());
            return ResponseEntity.ok().build();
        }).orElse(ResponseEntity.notFound().build());
    }
}
