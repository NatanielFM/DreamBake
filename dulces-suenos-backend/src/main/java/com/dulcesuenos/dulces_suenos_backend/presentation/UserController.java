package com.dulcesuenos.dulces_suenos_backend.presentation;

import com.dulcesuenos.dulces_suenos_backend.database.entity.User;
import com.dulcesuenos.dulces_suenos_backend.presentation.dto.RegisterRequest;
import com.dulcesuenos.dulces_suenos_backend.service.UserService;
import com.dulcesuenos.dulces_suenos_backend.util.JwtUtil;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public UserController(UserService userService, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        var opt = userService.register(req.getName(), req.getEmail(), req.getPhone(), req.getPassword());
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(opt.get());
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, Object> payload) {
        String email = (String) payload.get("email");
        String password = (String) payload.get("password");
        boolean remember = payload.get("remember") != null && Boolean.TRUE.equals(payload.get("remember"));

        if (email == null || password == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        Optional<User> userOpt = userService.findByEmail(email);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userOpt.get();
        if (!passwordEncoder.matches(password, user.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Generar JWT
        String token = jwtUtil.generateToken(user.getEmail(), remember);

        // Calcular maxAge en segundos (si remember -> expiración larga; si no -> cookie de sesión)
        long expiryMillis = jwtUtil.parseToken(token).getBody().getExpiration().getTime();
        long nowMillis = System.currentTimeMillis();
        long maxAgeSeconds = (expiryMillis > nowMillis) ? (expiryMillis - nowMillis) / 1000 : -1;

        ResponseCookie cookie = ResponseCookie.from("DS_TOKEN", token)
                .httpOnly(true)
                .secure(true) // poner true en producción con HTTPS
                .path("/")
                .maxAge(maxAgeSeconds > 0 ? maxAgeSeconds : -1)
                .sameSite("None")
                .build();

        user.setPassword(null);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(user);
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@CookieValue(name = "DS_TOKEN", required = false) String token) {
        if (token == null || token.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            if (!jwtUtil.isTokenValid(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            String email = jwtUtil.getSubject(token);
            Optional<User> u = userService.findByEmail(email);
            if (u.isEmpty()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            User user = u.get();
            user.setPassword(null);
            return ResponseEntity.ok(user);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        ResponseCookie cookie = ResponseCookie.from("DS_TOKEN", "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(0)
                .sameSite("None")
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .build();
    }
}
