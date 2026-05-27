package com.dulcesuenos.dulces_suenos_backend.presentation;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.dulcesuenos.dulces_suenos_backend.database.entity.User;
import com.dulcesuenos.dulces_suenos_backend.database.repository.UserRepository;



import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    // Registrar usuario
    @PostMapping("/register")
    public User register(@RequestBody User user) {
        user.setEnabled(true);
        user.setCreatedAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    // Listar usuarios (para pruebas)
    @GetMapping
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    // Login básico
    @PostMapping("/login")
    public String login(@RequestBody User loginUser) {
        return userRepository.findByEmail(loginUser.getEmail())
                .filter(u -> u.getPassword().equals(loginUser.getPassword()))
                .map(u -> "Login exitoso")
                .orElse("Credenciales inválidas");
    }
}
