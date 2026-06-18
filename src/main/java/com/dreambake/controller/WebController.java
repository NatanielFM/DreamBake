package com.dreambake.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebController {

    // Tu mapeo original del index (si ya lo tienes así, déjalo)
    @GetMapping("/")
    public String index() {
        return "index";
    }

    // NUEVO MAPEO: El constructor de tortas
    @GetMapping("/builder")
    public String cakeBuilder() {
        return "builder"; // Busca el archivo builder.html en src/main/resources/templates/
    }

    // ... (otros mapeos como /contact, /login, /admin, etc.)
}