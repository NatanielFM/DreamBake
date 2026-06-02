package com.dulcesuenos.dulces_suenos_backend.service;

import com.dulcesuenos.dulces_suenos_backend.database.entity.User;
import com.dulcesuenos.dulces_suenos_backend.database.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Optional<User> register(String name, String email, String phone, String rawPassword) {
        if (email == null || rawPassword == null || name == null) {
            return Optional.empty();
        }
        if (userRepository.findByEmail(email).isPresent()) {
            return Optional.empty();
        }
        User u = new User();
        u.setName(name);
        u.setEmail(email);
        u.setPhone(phone);
        u.setPassword(passwordEncoder.encode(rawPassword));
        u.setEnabled(true);
        u.setCreatedAt(LocalDateTime.now());
        User saved = userRepository.save(u);
        saved.setPassword(null);
        return Optional.of(saved);
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }
}
