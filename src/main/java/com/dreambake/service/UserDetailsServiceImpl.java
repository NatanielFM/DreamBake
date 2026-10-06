package com.dreambake.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.dreambake.entity.User;
import com.dreambake.repository.UserRepository;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPassword())
                // Usamos .authorities() en lugar de .roles() para usar nombres exactos
                // Esto tomará el valor que tengas en el campo 'roles' de tu BD (ej: "ADMIN")
                .authorities(user.getRoles() != null ? user.getRoles() : "")
                // Cuenta deshabilitada desde la base (enabled = false): no puede entrar
                .disabled(Boolean.FALSE.equals(user.getEnabled()))
                // Bloqueada 15 minutos después de 5 contraseñas incorrectas (LoginAttemptService)
                .accountLocked(loginAttemptService.isBlocked(email))
                .build();
    }
}
