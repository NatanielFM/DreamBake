package com.dreambake.controller;

import com.dreambake.entity.Product;
import com.dreambake.repository.CategoryRepository;
import com.dreambake.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.stream.Collectors;

@Controller
public class WebController {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/builder")
    public String cakeBuilder() {
        return "builder";
    }

    @GetMapping("/cart")
    public String cartPage() {
        return "cart";
    }

    @GetMapping("/products")
    public String productsPage(Model model) {
        List<Product> activeProducts = productRepository.findAll().stream()
                .filter(p -> p.isEnabled())
                .collect(Collectors.toList());

        model.addAttribute("products", activeProducts);
        model.addAttribute("categories", categoryRepository.findAll());
        return "products";
    }

    // Dejamos lista la ruta de perfil para el Punto 4
    @GetMapping("/profile")
    public String profilePage() {
        return "profile";
    }
}