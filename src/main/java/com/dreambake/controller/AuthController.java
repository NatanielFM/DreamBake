package com.dreambake.controller;

import com.dreambake.entity.User;
import com.dreambake.service.UserService;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PostMapping;
import com.dreambake.repository.UserRepository;

// Este controlador gestiona el ciclo de vida de la autenticación. Es el encargado de recibir los datos
// del usuario cuando se registra, validar que la información sea correcta y, si todo está bien, guardarla
// en el sistema, además de mostrar las páginas login y registro

@Controller
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    // Seguridad: del formulario de registro solo se aceptan estos campos.
    // Sin esto, alguien podría mandar a mano "id", "roles" o "enabled" y, por ejemplo,
    // pisar la cuenta de otro usuario o darse permisos de administrador.
    @InitBinder("user")
    public void allowOnlyRegisterFields(WebDataBinder binder) {
        binder.setAllowedFields("name", "email", "phone", "password");
    }

    // Muestra el formulario de registro
    @GetMapping("/register")
    public String showRegistrationForm(Model model) {
        model.addAttribute("user", new User()); // Paso un objeto vacío para el formulario
        return "register"; // Busca register.html en /templates
    }

    // Procesa el registro
    @PostMapping("/register")
    public String registerUser(@Valid @ModelAttribute("user") User user, BindingResult result, Model model) {
        // El email tiene que ser único: si ya existe, se avisa en el formulario
        if (user.getEmail() != null && userRepository.findByEmail(user.getEmail().trim()).isPresent()) {
            result.rejectValue("email", "duplicate", "Ya existe una cuenta con ese email.");
        }
        if (result.hasErrors()) {
            return "register"; // Si hay error, vuelve a cargar el formulario con los mensajes
        }

        userService.registerUser(user);
        return "redirect:/login"; // Redirige tras registrar
    }

    // Muestra el formulario de login
    @GetMapping("/login")
    public String login() {
        return "login"; // Buscará login.html en /templates
    }
}
