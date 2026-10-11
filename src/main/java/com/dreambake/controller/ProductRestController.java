package com.dreambake.controller;

import com.dreambake.audit.Auditable;
import com.dreambake.audit.AuditText;
import com.dreambake.entity.AuditAction;
import com.dreambake.entity.AuditModule;
import com.dreambake.service.AuditService;
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
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class ProductRestController {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    // Registro de auditoría (paso 3.3): se usa directo donde hace falta el antes y el después
    @Autowired
    private AuditService audit;

    // ================= PRODUCTOS =================

    @GetMapping("/products")
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    @PostMapping("/products")
    @Auditable(action = AuditAction.CREATE, module = AuditModule.PRODUCTOS, entityType = "Producto", entityId = "#result.id",
            description = "Creó el producto «#{#result.name}» a #{T(com.dreambake.audit.AuditText).money(#result.price)}")
    public Product createProduct(@Valid @RequestBody ProductRequest request) {
        return saveOrUpdateProduct(new Product(), request);
    }

    @PutMapping("/products/{id}")
    public ResponseEntity<Product> updateProduct(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return productRepository.findById(id)
                .map(product -> {
                    // Antes y después (por ejemplo, un cambio de precio): se llama directo a la auditoría
                    Map<String, Object> before = productSnapshot(product);
                    Product saved = saveOrUpdateProduct(product, request);
                    Map<String, Object> diff = AuditText.diff(before, productSnapshot(saved));
                    String priceText = Objects.equals(before.get("precio"), saved.getPrice()) ? ""
                            : " (precio: " + AuditText.money((Double) before.get("precio")) + " → " + AuditText.money(saved.getPrice()) + ")";
                    audit.log(AuditAction.UPDATE, AuditModule.PRODUCTOS, "Producto", saved.getId(),
                            "Editó el producto «" + saved.getName() + "»" + priceText + AuditText.changedFields(diff), diff);
                    return ResponseEntity.ok(saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        Optional<Product> found = productRepository.findById(id);
        if (found.isPresent()) {
            Map<String, Object> before = productSnapshot(found.get());
            productRepository.deleteById(id);
            audit.log(AuditAction.DELETE, AuditModule.PRODUCTOS, "Producto", id,
                    "Eliminó el producto «" + found.get().getName() + "»", AuditText.change(before, null));
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    // Los datos de un producto que se comparan en la auditoría
    private Map<String, Object> productSnapshot(Product p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nombre", p.getName());
        m.put("precio", p.getPrice());
        m.put("descripcion", p.getDescription());
        m.put("visible", p.isEnabled());
        m.put("categoria", p.getCategory() == null ? null : p.getCategory().getName());
        m.put("foto", p.getImageUrl());
        m.put("receta", p.getRecipe() == null ? null : p.getRecipe().getId());
        return m;
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
    @Auditable(action = AuditAction.CREATE, module = AuditModule.PRODUCTOS, entityType = "Categoría", entityId = "#result.id",
            description = "Creó la categoría «#{#result.name}»")
    public Category createCategory(@RequestBody Category category) {
        long count = categoryRepository.count();
        category.setDisplayOrder((int) count); // Se va al final de la lista por defecto
        return categoryRepository.save(category);
    }

    @PutMapping("/categories/{id}")
    @Auditable(action = AuditAction.UPDATE, module = AuditModule.PRODUCTOS, entityType = "Categoría", entityId = "#id",
            description = "Cambió el nombre de una categoría a «#{#body.name}»")
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
            audit.log(AuditAction.DELETE, AuditModule.PRODUCTOS, "Categoría", id,
                    "Eliminó la categoría «" + cat.getName() + "» (" + products.size() + (products.size() == 1 ? " producto pasó" : " productos pasaron") + " a «Sin tipo»)", null);
            return ResponseEntity.ok().build();
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/categories/reorder")
    @Auditable(action = AuditAction.UPDATE, module = AuditModule.PRODUCTOS, entityType = "Categoría",
            description = "Cambió el orden de las categorías")
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