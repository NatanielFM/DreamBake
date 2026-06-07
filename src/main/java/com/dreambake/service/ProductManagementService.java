package com.dreambake.service;

import com.dreambake.entity.Category;
import com.dreambake.entity.Product;
import com.dreambake.repository.CategoryRepository;
import com.dreambake.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class ProductManagementService {

    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ProductRepository productRepository;

    // Se asegura de que "Sin tipo" exista siempre
    public Category getOrCreateDefaultCategory() {
        return categoryRepository.findByName("Sin tipo").orElseGet(() -> {
            Category cat = new Category();
            cat.setName("Sin tipo");
            cat.setDeletable(false);
            return categoryRepository.save(cat);
        });
    }

    @Transactional
    public void deleteCategorySafely(Long categoryId) {
        Category categoryToDelete = categoryRepository.findById(categoryId).orElseThrow();
        if (!categoryToDelete.isDeletable()) {
            throw new RuntimeException("No se puede eliminar la categoría por defecto.");
        }

        Category defaultCategory = getOrCreateDefaultCategory();
        List<Product> products = productRepository.findByCategory(categoryToDelete);

        // Reasignamos los productos a "Sin tipo"
        for (Product p : products) {
            p.setCategory(defaultCategory);
            productRepository.save(p);
        }

        categoryRepository.delete(categoryToDelete);
    }
}
