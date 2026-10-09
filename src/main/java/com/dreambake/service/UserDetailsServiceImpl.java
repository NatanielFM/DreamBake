package com.dreambake.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.dreambake.entity.User;
import com.dreambake.repository.UserRepository;
import com.dreambake.util.ClientIpResolver;

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
                // Bloqueada 15 minutos después de 5 contraseñas incorrectas desde esta IP (LoginAttemptService)
                .accountLocked(loginAttemptService.isBlocked(email, currentClientIp()))
                .build();
    }

    // Este método no recibe el pedido HTTP; se lo pide a Spring (RequestContextHolder),
    // que guarda el pedido en curso. Si no hay pedido (no debería pasar en el login), se usa "desconocida".
    private String currentClientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return ClientIpResolver.resolve(attrs.getRequest());
        }
        return "desconocida";
    }
}
