package com.dreambake.controller;

import com.dreambake.entity.Category;
import com.dreambake.entity.Product;
import com.dreambake.repository.CategoryRepository;
import com.dreambake.repository.ProductRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class ProductRestController {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    // ================= PRODUCTOS =================

    @GetMapping("/products")
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    @PostMapping("/products")
    public Product createProduct(@Valid @RequestBody ProductRequest request) {
        return saveOrUpdateProduct(new Product(), request);
    }

    @PutMapping("/products/{id}")
    public ResponseEntity<Product> updateProduct(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
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

    private Product saveOrUpdateProduct(Product product, ProductRequest request) {
        product.setName(request.name());
        product.setPrice(request.price());
        product.setDescription(request.description());
        product.setEnabled(request.enabled());
        product.setImageUrl(request.imageUrl());
        product.setRecipe(request.recipe());

        Category category = categoryRepository.findByName(request.categoryName())
                .orElseGet(() -> categoryRepository.findByName("Sin tipo").orElse(null));

        product.setCategory(category);
        return productRepository.save(product);
    }

    public record ProductRequest(
            @NotBlank(message = "El nombre del producto no puede estar vacío.") String name,
            @NotNull(message = "Debes asignar un precio.") @Positive(message = "El precio debe ser mayor a 0.") Double price,
            String description, boolean enabled, String categoryName, String imageUrl, com.dreambake.entity.Recipe recipe) {
    }

    // ================= CATEGORÍAS (TIPOS) =================

    @GetMapping("/categories")
    public List<Category> getAllCategories() {
        // Devuelve las categorías ordenadas por la posición que el usuario eligió
        return categoryRepository.findAll(Sort.by(Sort.Direction.ASC, "displayOrder"));
    }

    @PostMapping("/categories")
    public Category createCategory(@RequestBody Category category) {
        long count = categoryRepository.count();
        category.setDisplayOrder((int) count); // Se va al final de la lista por defecto
        return categoryRepository.save(category);
    }

    @PutMapping("/categories/{id}")
    public ResponseEntity<Category> updateCategory(@PathVariable Long id, @RequestBody Category request) {
        return categoryRepository.findById(id).map(cat -> {
            cat.setName(request.getName());
            return ResponseEntity.ok(categoryRepository.save(cat));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<?> deleteCategory(@PathVariable Long id) {
        return categoryRepository.findById(id).map(cat -> {
            if (!cat.isDeletable()) {
                return ResponseEntity.badRequest().body("No se puede eliminar la categoría por defecto.");
            }
            // Mover los productos de esta categoría a "Sin tipo"
            Category defaultCat = categoryRepository.findByName("Sin tipo").orElse(null);
            List<Product> products = productRepository.findByCategory(cat);
            for (Product p : products) {
                p.setCategory(defaultCat);
                productRepository.save(p);
            }
            categoryRepository.delete(cat);
            return ResponseEntity.ok().build();
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/categories/reorder")
    public ResponseEntity<?> reorderCategories(@RequestBody List<Long> orderedIds) {
        for (int i = 0; i < orderedIds.size(); i++) {
            Long id = orderedIds.get(i);
            Optional<Category> optCat = categoryRepository.findById(id);
            if (optCat.isPresent()) {
                Category cat = optCat.get();
                cat.setDisplayOrder(i); // Actualiza la posición exacta (1, 2, 3...)
                categoryRepository.save(cat);
            }
        }
        return ResponseEntity.ok().build();
    }
}