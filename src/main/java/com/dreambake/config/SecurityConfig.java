package com.dreambake.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // 1. AGREGADO: /builder permitido para que entren a la página
                        .requestMatchers("/", "/products", "/builder", "/register", "/css/**", "/js/**", "/img/**",
                                "/uploads/**")
                        .permitAll()

                        // 2. AGREGADO: Permitimos peticiones a la API de pedidos (para guardar el
                        // carrito)
                        .requestMatchers("/api/orders/**").permitAll()

                        // 3. El resto de /api/ y /admin/ sigue restringido solo para personal
                        .requestMatchers("/admin/**", "/api/**").hasAnyAuthority("ADMIN", "PASTELERO")

                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .successHandler(authenticationSuccessHandler())
                        .permitAll())
                .logout(logout -> logout.permitAll());

        return http.build();
    }

    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> {
            boolean isAdminOrBaker = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ADMIN") || a.getAuthority().equals("PASTELERO"));

            if (isAdminOrBaker) {
                response.sendRedirect("/admin");
            } else {
                response.sendRedirect("/");
            }
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}