package com.dulcesuenos.dulces_suenos_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // 🔹 Desactiva CSRF
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll() // 🔹 Abre absolutamente todos los endpoints
            );
        return http.build();
    }
}

