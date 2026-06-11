package com.dreambake.controller;

import com.dreambake.entity.Category;
import com.dreambake.entity.Product;
import com.dreambake.repository.CategoryRepository;
import com.dreambake.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ProductRestController {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    // --- ENDPOINTS DE PRODUCTOS ---

    @GetMapping("/products")
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    @PostMapping("/products")
    public Product createProduct(@RequestBody ProductRequest request) {
        return saveOrUpdateProduct(new Product(), request);
    }

    @PutMapping("/products/{id}")
    public ResponseEntity<Product> updateProduct(@PathVariable Long id, @RequestBody ProductRequest request) {
        return productRepository.findById(id)
                .map(product -> ResponseEntity.ok(saveOrUpdateProduct(product, request)))
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        if (productRepository.existsById(id)) {
            productRepository.deleteById(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    // --- ENDPOINTS DE CATEGORÍAS ---

    @GetMapping("/categories")
    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    @PostMapping("/categories")
    public Category createCategory(@RequestBody Category category) {
        return categoryRepository.save(category);
    }

    // --- MÉTODOS DE AYUDA ---

    private Product saveOrUpdateProduct(Product product, ProductRequest request) {
        product.setName(request.name());
        product.setPrice(request.price());
        product.setDescription(request.description());
        product.setEnabled(request.enabled());
        product.setImageUrl(request.imageUrl());

        // Buscar categoría, si no existe o es nula, asignar "Sin tipo"
        Category category = categoryRepository.findByName(request.categoryName())
                .orElseGet(() -> categoryRepository.findByName("Sin tipo").orElse(null));

        product.setCategory(category);
        return productRepository.save(product);
    }

    // DTO (Data Transfer Object) para recibir datos limpios de JS
    public record ProductRequest(String name, Double price, String description, boolean enabled, String categoryName,
            String imageUrl) {
    }
}