package com.dreambake.controller;

import com.dreambake.entity.Customer;
import com.dreambake.repository.CustomerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

/**
 * Página pública para darse de baja de los emails promocionales: /baja?token=...
 *
 * Cada email promocional trae este link con el código secreto del cliente (unsubscribeToken).
 * Funciona en dos pasos a propósito:
 *  1. GET  /baja?token=…  muestra "¿Querés dejar de recibir promociones?" con un botón. NO cambia nada.
 *  2. POST /baja          (el botón) desactiva el permiso y muestra "Te diste de baja".
 * Gmail y algunos antivirus abren solos los links de los emails para revisarlos: si la baja
 * ocurriera con solo abrir el link, un cliente podría quedar dado de baja sin haberlo pedido.
 *
 * La página "baja.html" tiene cuatro estados (variable "state"):
 *   confirm = pregunta con botón · done = se dio de baja · already = ya estaba dado de baja · invalid = link no válido
 */
@Controller
public class UnsubscribeController {

    @Autowired
    private CustomerRepository customerRepository;

    @GetMapping("/baja")
    public String showUnsubscribe(@RequestParam(required = false) String token, Model model) {
        Optional<Customer> customer = findByToken(token);
        if (customer.isEmpty()) {
            model.addAttribute("state", "invalid");
        } else if (!Boolean.TRUE.equals(customer.get().getMarketingConsent())) {
            model.addAttribute("state", "already");
        } else {
            model.addAttribute("state", "confirm");
            model.addAttribute("token", token);
            model.addAttribute("firstName", firstName(customer.get().getName()));
        }
        return "baja";
    }

    @PostMapping("/baja")
    public String confirmUnsubscribe(@RequestParam(required = false) String token, Model model) {
        Optional<Customer> customer = findByToken(token);
        if (customer.isEmpty()) {
            model.addAttribute("state", "invalid");
            return "baja";
        }
        Customer c = customer.get();
        c.setMarketingConsent(false);
        customerRepository.save(c);
        model.addAttribute("state", "done");
        return "baja";
    }

    // Busca al cliente por su código de baja (un UUID de 36 caracteres)
    private Optional<Customer> findByToken(String token) {
        if (token == null || token.isBlank() || token.length() > 36) return Optional.empty();
        return customerRepository.findByUnsubscribeToken(token.trim());
    }

    // Solo el primer nombre, para saludar ("Ana" en vez de "Ana María Pérez")
    private String firstName(String name) {
        if (name == null || name.isBlank()) return null;
        return name.trim().split("\\s+")[0];
    }
}
