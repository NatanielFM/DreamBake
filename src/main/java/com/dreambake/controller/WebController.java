package com.dreambake.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebController {

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/builder")
    public String cakeBuilder() {
        return "builder";
    }

    // NUEVA RUTA PARA EL CARRITO
    @GetMapping("/cart")
    public String cartPage() {
        return "cart";
    }

    // RUTA PARA PRODUCTOS PUBLICOS (Asegúrate de tenerla)
    @GetMapping("/products")
    public String productsPage() {
        return "products";
    }
}