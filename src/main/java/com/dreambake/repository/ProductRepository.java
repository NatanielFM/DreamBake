package com.dreambake.repository;

import com.dreambake.entity.Category;
import com.dreambake.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByCategory(Category category);

    java.util.Optional<Product> findByName(String name);

    java.util.Optional<Product> findByNameIgnoreCase(String name); // NUEVO
}
