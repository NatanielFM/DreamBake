package com.dreambake.controller;

import com.dreambake.entity.User;
import com.dreambake.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserRestController {

    @Autowired
    private UserService userService;

    // Obtener solo usuarios con rol PASTELERO
    @GetMapping("/pasteleros")
    public List<User> getPasteleros() {
        return userService.getPasteleros();
    }

    // Crear o actualizar un pastelero
    @PostMapping("/pasteleros")
    public ResponseEntity<?> savePastelero(@RequestBody User user) {
        try {
            User saved = userService.savePastelero(user);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Eliminar un usuario
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        try {
            userService.deleteUser(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
