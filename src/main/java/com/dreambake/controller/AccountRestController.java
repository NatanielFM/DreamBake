package com.dreambake.controller;

import com.dreambake.entity.User;
import com.dreambake.repository.UserRepository;
import com.dreambake.service.CustomerAccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * API del área del cliente (la usa profile.js).
 *
 * Seguridad: no recibe ningún parámetro. El usuario sale de la sesión (Authentication), así que
 * cada uno solo puede ver lo suyo: no hay forma de pedir los pedidos de otro email.
 */
@RestController
@RequestMapping("/api/account")
public class AccountRestController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomerAccountService accountService;

    // "Mis pedidos": GET /api/account/orders
    @GetMapping("/orders")
    public ResponseEntity<?> myOrders(Authentication authentication) {
        User user = authentication == null ? null
                : userRepository.findByEmail(authentication.getName()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Iniciá sesión."));
        }
        if (!accountService.canUseCustomerArea(user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Confirmá tu email para ver tus pedidos."));
        }
        return ResponseEntity.ok(accountService.myOrders(user));
    }
}
