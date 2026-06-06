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
}