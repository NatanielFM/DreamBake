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
                        .requestMatchers("/", "/builder", "/products", "/cart", "/register", "/css/**", "/js/**",
                                "/img/**", "/uploads/**")
                        .permitAll()
                        .requestMatchers("/api/orders/**", "/api/cake-config/public", "/api/coupons/validate/**", "/api/coupons/consume/**").permitAll()
                        .requestMatchers("/admin/**").hasAuthority("ADMIN")
                        .requestMatchers("/baker/**").hasAuthority("PASTELERO")
                        .requestMatchers("/api/**").hasAnyAuthority("ADMIN", "PASTELERO")
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
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ADMIN"));
            boolean isBaker = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("PASTELERO"));

            if (isAdmin) {
                response.sendRedirect("/admin");
            } else if (isBaker) {
                response.sendRedirect("/baker");
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