package com.dreambake.controller;

import com.dreambake.service.BakeyService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * API de Bakey, el asistente virtual (paso 5.1). Solo ADMIN y PASTELERO (regla en SecurityConfig).
 *
 *   GET  /api/bakey/status  → si Bakey está configurado (hay clave de Gemini) o no
 *   POST /api/bakey/chat    → {"message": "…"} → {"status": "OK", "reply": "…"}
 *   POST /api/bakey/reset   → empieza una conversación nueva
 *
 * Siempre devuelve un "reply" que se puede mostrar tal cual a la persona (nunca errores técnicos).
 * No se registran en la auditoría porque no cambian ningún dato del sistema (como las vistas previas).
 * Las acciones que haga Bakey desde el paso 5.4 sí se van a registrar (ASSISTANT_ACTION).
 */
@RestController
@RequestMapping("/api/bakey")
public class BakeyRestController {

    @Autowired
    private BakeyService bakeyService;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "Bakey");
        body.put("configured", bakeyService.isConfigured());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody(required = false) Map<String, Object> request,
                                                    Authentication authentication, HttpSession session) {
        Object message = request == null ? null : request.get("message");
        BakeyService.BakeyAnswer answer = bakeyService.chat(
                authentication.getName(), session, message instanceof String s ? s : null);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", answer.status());
        body.put("reply", answer.reply());
        return ResponseEntity.status(httpStatusFor(answer.status())).body(body);
    }

    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> reset(HttpSession session) {
        bakeyService.reset(session);
        return ResponseEntity.ok(Map.of("status", "OK"));
    }

    // Código HTTP según cómo terminó el mensaje (el texto para la persona va siempre en "reply")
    private HttpStatus httpStatusFor(BakeyService.Status status) {
        return switch (status) {
            case OK -> HttpStatus.OK;
            case EMPTY, TOO_LONG -> HttpStatus.BAD_REQUEST;
            case LIMIT, BUSY -> HttpStatus.TOO_MANY_REQUESTS;           // límite propio o cuota de Gemini
            case NOT_CONFIGURED -> HttpStatus.SERVICE_UNAVAILABLE;
            case BLOCKED -> HttpStatus.UNPROCESSABLE_CONTENT;           // Gemini no quiso responder eso
            case TIMEOUT, ERROR -> HttpStatus.BAD_GATEWAY;              // falló la conexión con Gemini
        };
    }
}
