package com.dreambake.service;

import com.dreambake.entity.User;
import com.dreambake.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public void registerUser(User user) {
        // Ciframos la contraseña antes de guardarla
        String encodedPassword = passwordEncoder.encode(user.getPassword());
        user.setPassword(encodedPassword);

        // Por defecto le asignamos el rol de usuario
        user.setRoles("ROLE_USER");
        // Guardamos en la base de datos
        userRepository.save(user);
    }

    public java.util.List<User> getPasteleros() {
        return userRepository.findByRoles("PASTELERO");
    }

    public User savePastelero(User user) {
        if (user.getId() != null) {
            User existing = userRepository.findById(user.getId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
            user.setCreatedAt(existing.getCreatedAt());
            if (user.getPassword() == null || user.getPassword().isEmpty()) {
                user.setPassword(existing.getPassword());
            } else {
                user.setPassword(passwordEncoder.encode(user.getPassword()));
            }
        } else {
            if (user.getPassword() != null && !user.getPassword().isEmpty()) {
                user.setPassword(passwordEncoder.encode(user.getPassword()));
            }
        }
        user.setRoles("PASTELERO");
        return userRepository.save(user);
    }

    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }
}