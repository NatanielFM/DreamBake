package com.dulcesuenos.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        String backendUrl = "http://localhost:8080/users/register";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> reqEntity = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<String> backendResp = restTemplate.postForEntity(backendUrl, reqEntity, String.class);
            return forwardResponseWithCookies(backendResp, request);
        } catch (HttpClientErrorException ex) {
            return forwardErrorWithCookies(ex, request);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{}");
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        String backendUrl = "http://localhost:8080/users/login";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> reqEntity = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<String> backendResp = restTemplate.postForEntity(backendUrl, reqEntity, String.class);
            return forwardResponseWithCookies(backendResp, request);
        } catch (HttpClientErrorException ex) {
            return forwardErrorWithCookies(ex, request);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{}");
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpServletRequest request) {
        String backendUrl = "http://localhost:8080/users/me";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Forward Cookie header from client to backend
        String cookieHeader = request.getHeader(HttpHeaders.COOKIE);
        if (cookieHeader != null) headers.add(HttpHeaders.COOKIE, cookieHeader);

        HttpEntity<Void> reqEntity = new HttpEntity<>(headers);
        try {
            ResponseEntity<String> backendResp = restTemplate.exchange(backendUrl, HttpMethod.GET, reqEntity, String.class);
            return forwardResponseWithCookies(backendResp, request);
        } catch (HttpClientErrorException ex) {
            return forwardErrorWithCookies(ex, request);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{}");
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        String backendUrl = "http://localhost:8080/users/logout";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Forward Cookie header from client to backend (so backend can clear it)
        String cookieHeader = request.getHeader(HttpHeaders.COOKIE);
        if (cookieHeader != null) headers.add(HttpHeaders.COOKIE, cookieHeader);

        HttpEntity<Void> reqEntity = new HttpEntity<>(headers);
        try {
            ResponseEntity<String> backendResp = restTemplate.postForEntity(backendUrl, reqEntity, String.class);
            return forwardResponseWithCookies(backendResp, request);
        } catch (HttpClientErrorException ex) {
            return forwardErrorWithCookies(ex, request);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{}");
        }
    }

    /* ---------- Helpers para reenviar Set-Cookie, CORS y body ---------- */
    private ResponseEntity<?> forwardResponseWithCookies(ResponseEntity<String> backendResp, HttpServletRequest clientRequest) {
        HttpHeaders respHeaders = new HttpHeaders();
        respHeaders.setContentType(MediaType.APPLICATION_JSON);

        // Reenviar Set-Cookie(s) del backend al cliente
        List<String> setCookies = backendResp.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (setCookies != null) {
            for (String c : setCookies) {
                respHeaders.add(HttpHeaders.SET_COOKIE, c);
            }
        }

        // Añadir CORS headers necesarios para que el navegador acepte cookies cross-origin
        String origin = clientRequest.getHeader("Origin");
        if (origin != null && !origin.isBlank()) {
            respHeaders.add("Access-Control-Allow-Origin", origin);
            respHeaders.add("Access-Control-Allow-Credentials", "true");
            // Exponer Set-Cookie para depuración si fuera necesario
            respHeaders.add("Access-Control-Expose-Headers", "Set-Cookie,Content-Type");
        }

        // Log simple para depuración (opcional)
        System.out.println("[AuthController] Forwarding backend headers: " + backendResp.getHeaders());

        String body = backendResp.getBody();
        if (body == null || body.isBlank()) body = "{}";

        return new ResponseEntity<>(body, respHeaders, backendResp.getStatusCode());
    }

    private ResponseEntity<?> forwardErrorWithCookies(HttpClientErrorException ex, HttpServletRequest clientRequest) {
        HttpHeaders respHeaders = new HttpHeaders();
        respHeaders.setContentType(MediaType.APPLICATION_JSON);

        List<String> setCookies = ex.getResponseHeaders() != null ? ex.getResponseHeaders().get(HttpHeaders.SET_COOKIE) : null;
        if (setCookies != null) {
            for (String c : setCookies) respHeaders.add(HttpHeaders.SET_COOKIE, c);
        }

        String origin = clientRequest.getHeader("Origin");
        if (origin != null && !origin.isBlank()) {
            respHeaders.add("Access-Control-Allow-Origin", origin);
            respHeaders.add("Access-Control-Allow-Credentials", "true");
            respHeaders.add("Access-Control-Expose-Headers", "Set-Cookie,Content-Type");
        }

        String body = ex.getResponseBodyAsString();
        if (body == null || body.isBlank()) body = "{}";

        return new ResponseEntity<>(body, respHeaders, ex.getStatusCode());
    }
}
