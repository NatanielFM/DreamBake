package com.dreambake.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebController {

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("titulo", "Pastelería DreamBake");
        model.addAttribute("mensaje", "¡Bienvenidos a la pastelería! Prueba nuestro nuevo pastel.");
        return "index"; // Busca index en src/main/resources/templates/index.html
    }
}
