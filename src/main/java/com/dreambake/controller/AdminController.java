package com.dreambake.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

// Este controlador es el encargado de gestionar todas las peticiones que llegan bajo la ruta /admin.
// Su única responsabilidad es escuchar la petición del usuario y devolver la vista correspondiente.

@Controller // Le dice a Spring: "Esta clase es un componente que va a manejar peticiones
            // web"
@RequestMapping("/admin") // Le indica a Spring: que todos los métodos que estén dentro de esta clase
// van a empezar con la dirección /admin. Si creo un método test sería
// /admin/test
public class AdminController {

    @GetMapping // Define el tipo de petición. Cuando un usuario escriba www.dominio.com/admin
                // en su
    // navegador el sistema ejecutará este método porque es una petición tipo GET
    public String adminPanel() {
        return "admin"; // Esto busca admin.html en /templates
    }
}
