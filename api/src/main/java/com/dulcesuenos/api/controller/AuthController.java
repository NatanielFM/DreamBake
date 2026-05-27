package com.dulcesuenos.api.controller;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import org.springframework.web.client.RestTemplate;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> payload) {
        String backendUrl = "http://localhost:8080/users/login";
        ResponseEntity<String> response = restTemplate.postForEntity(backendUrl, payload, String.class);

        // 🔹 Si el backend devuelve texto plano, lo envolvemos en JSON
        Map<String, String> body = Map.of("message", response.getBody());

        return ResponseEntity.status(response.getStatusCode()).body(body);
    }


    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> payload) {
        String backendUrl = "http://localhost:8080/users/register";
        ResponseEntity<String> response = restTemplate.postForEntity(backendUrl, payload, String.class);
        return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
    }
}
