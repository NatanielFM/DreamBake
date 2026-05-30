package com.dulcesuenos.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> payload) {
        String backendUrl = "http://localhost:8080/users/login";
        HttpEntity<Map<String, String>> request = new HttpEntity<>(payload);

        try {
            // Intentamos llamar al backend (caso 2xx)
            ResponseEntity<String> response = restTemplate.postForEntity(backendUrl, request, String.class);

            // Convertimos el body (String) a Map para devolver JSON consistente
            Map<String, Object> body = Map.of("message", response.getBody());
            try {
                // Si el backend ya devolvió JSON, preferimos parsearlo a Map
                Map parsed = mapper.readValue(response.getBody(), Map.class);
                return ResponseEntity.status(response.getStatusCode()).body(parsed);
            } catch (Exception ignore) {
                return ResponseEntity.status(response.getStatusCode()).body(body);
            }

        } catch (HttpClientErrorException ex) {
            // El backend devolvió 4xx/5xx: capturamos status y body (si existe) y lo reenviamos
            String respBody = ex.getResponseBodyAsString();
            Map<String, Object> body;
            if (respBody == null || respBody.isBlank()) {
                body = Map.of("message", ex.getStatusText());
            } else {
                try {
                    body = mapper.readValue(respBody, Map.class);
                } catch (Exception parseEx) {
                    body = Map.of("message", respBody);
                }
            }
            return ResponseEntity.status(ex.getStatusCode()).body(body);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(Map.of("message", "Error interno en la API intermedia"));
        }
    }

    // Mantener register si lo necesitás (opcional)
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> payload) {
        String backendUrl = "http://localhost:8080/users/register";
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(backendUrl, payload, String.class);
            // Intentamos parsear JSON devuelto por backend
            try {
                Map parsed = mapper.readValue(response.getBody(), Map.class);
                return ResponseEntity.status(response.getStatusCode()).body(parsed);
            } catch (Exception ignore) {
                return ResponseEntity.status(response.getStatusCode()).body(Map.of("message", response.getBody()));
            }
        } catch (HttpClientErrorException ex) {
            String respBody = ex.getResponseBodyAsString();
            Map<String, Object> body;
            try {
                body = mapper.readValue(respBody, Map.class);
            } catch (Exception parseEx) {
                body = Map.of("message", respBody == null ? "Error" : respBody);
            }
            return ResponseEntity.status(ex.getStatusCode()).body(body);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(Map.of("message", "Error interno en la API intermedia"));
        }
    }
}
