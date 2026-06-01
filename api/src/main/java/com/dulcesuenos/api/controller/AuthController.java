package com.dulcesuenos.api.controller;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final RestTemplate restTemplate = new RestTemplate();

    // Reenvía /register al backend (:8080). No añade mensajes; reenvía status y body crudo (o {}).
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, Object> payload) {
        String backendUrl = "http://localhost:8080/users/register";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(backendUrl, request, String.class);
            String respBody = response.getBody();
            if (respBody == null || respBody.isBlank()) respBody = "{}";
            return ResponseEntity.status(response.getStatusCode())
                                 .contentType(MediaType.APPLICATION_JSON)
                                 .body(respBody);
        } catch (HttpClientErrorException ex) {
            String respBody = ex.getResponseBodyAsString();
            if (respBody == null || respBody.isBlank()) respBody = "{}";
            return ResponseEntity.status(ex.getStatusCode())
                                 .contentType(MediaType.APPLICATION_JSON)
                                 .body(respBody);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .contentType(MediaType.APPLICATION_JSON)
                                 .body("{}");
        }
    }

    // Reenvía /login al backend (:8080). Igual política: backend es la fuente de datos.
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, Object> payload) {
        String backendUrl = "http://localhost:8080/users/login";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(backendUrl, request, String.class);
            String respBody = response.getBody();
            if (respBody == null || respBody.isBlank()) respBody = "{}";
            return ResponseEntity.status(response.getStatusCode())
                                 .contentType(MediaType.APPLICATION_JSON)
                                 .body(respBody);
        } catch (HttpClientErrorException ex) {
            String respBody = ex.getResponseBodyAsString();
            if (respBody == null || respBody.isBlank()) respBody = "{}";
            return ResponseEntity.status(ex.getStatusCode())
                                 .contentType(MediaType.APPLICATION_JSON)
                                 .body(respBody);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .contentType(MediaType.APPLICATION_JSON)
                                 .body("{}");
        }
    }
}
