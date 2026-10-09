package com.dreambake.controller;

import com.dreambake.entity.OrderStatus;
import com.dreambake.entity.Product;
import com.dreambake.repository.CategoryRepository;
import com.dreambake.repository.GalleryImageRepository;
import com.dreambake.repository.OrderRepository;
import com.dreambake.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
public class WebController {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private GalleryImageRepository galleryImageRepository;

    @Autowired
    private OrderRepository orderRepository;

    // Sticker "Más vendido": los 3 productos del catálogo que más se pidieron
    // (pedidos confirmados o entregados), siempre que se hayan pedido al menos 2 veces.
    // Se calcula solo con los pedidos: no hay que marcar nada a mano.
    private static final int BEST_SELLERS = 3;
    private static final long BEST_SELLER_MIN_SALES = 2;

    private Set<Long> findBestSellerIds(List<Product> products) {
        Set<Long> ids = new HashSet<>();
        List<Object[]> ranking = orderRepository.findTopProductsAggregation(
                List.of(OrderStatus.CONFIRMED, OrderStatus.IN_PROGRESS, OrderStatus.COMPLETED));
        for (Object[] row : ranking) {               // viene ordenado de más a menos vendido
            if (ids.size() >= BEST_SELLERS) break;
            String name = String.valueOf(row[0]);
            long count = ((Number) row[1]).longValue();
            if (count < BEST_SELLER_MIN_SALES) break;
            products.stream()
                    .filter(p -> p.getName().equalsIgnoreCase(name))
                    .findFirst()
                    .ifPresent(p -> ids.add(p.getId()));   // las tortas del creador no son del catálogo: se saltean
        }
        return ids;
    }

    @GetMapping("/")
    public String index(Model model) {
        // Vitrina de la home: hasta 8 productos activos (si no hay, la vista muestra solo las categorías)
        List<Product> activeProducts = productRepository.findAll().stream()
                .filter(Product::isEnabled)
                .collect(Collectors.toList());
        model.addAttribute("featuredProducts", activeProducts.stream().limit(8).collect(Collectors.toList()));
        model.addAttribute("bestSellerIds", findBestSellerIds(activeProducts));
        // Fotos que el admin sube desde la pestaña Galería del panel
        model.addAttribute("galleryImages", galleryImageRepository.findAll());
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
        model.addAttribute("bestSellerIds", findBestSellerIds(activeProducts));
        model.addAttribute("categories", categoryRepository.findAll());
        return "products";
    }
}