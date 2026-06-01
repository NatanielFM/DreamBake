package com.dulcesuenos.dulces_suenos_backend.presentation;

import com.dulcesuenos.dulces_suenos_backend.database.entity.User;
import com.dulcesuenos.dulces_suenos_backend.presentation.dto.RegisterRequest;
import com.dulcesuenos.dulces_suenos_backend.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import jakarta.validation.Valid;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    public UserController(UserService userService, PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Registro: recibe RegisterRequest (name, email, phone, password).
     * Devuelve 201 con el user (sin password) o 409 si el email ya existe.
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        // Validación mínima (RegisterRequest ya tiene anotaciones de validación)
        if (req.getEmail() == null || req.getPassword() == null || req.getName() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        // Delegar la lógica al servicio
        Optional<User> created = userService.register(req.getName(), req.getEmail(), req.getPhone(), req.getPassword());
        if (created.isEmpty()) {
            // Conflicto (email ya registrado o datos inválidos)
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        User saved = created.get();
        // Asegurarse de no devolver la contraseña
        saved.setPassword(null);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * Login: recibe JSON con email y password.
     * Devuelve 200 con user (sin password) o 401 sin body.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> payload) {
        String email = payload.get("email");
        String password = payload.get("password");

        if (email == null || password == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        Optional<User> userOpt = userService.findByEmail(email);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userOpt.get();
        if (!passwordEncoder.matches(password, user.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        user.setPassword(null);
        return ResponseEntity.ok(user);
    }
}
