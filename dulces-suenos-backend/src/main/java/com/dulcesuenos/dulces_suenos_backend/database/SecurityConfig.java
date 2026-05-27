package com.dulcesuenos.dulces_suenos_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // Desactiva CSRF para pruebas con Postman
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/users/**").permitAll() // Permite acceso libre a /users/*
                .anyRequest().authenticated()
            );
        return http.build();
    }
}
