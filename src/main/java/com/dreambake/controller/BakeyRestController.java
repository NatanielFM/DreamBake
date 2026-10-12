package com.dreambake.controller;

import com.dreambake.ai.tools.BakeyActionService;
import com.dreambake.entity.User;
import com.dreambake.repository.UserRepository;
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
 * API de Bakey, el asistente virtual (pasos 5.1 a 5.4). Solo ADMIN y PASTELERO (regla en SecurityConfig).
 *
 *   GET  /api/bakey/status                → si Bakey está configurado (hay clave de Gemini) o no
 *   POST /api/bakey/chat                  → {"message": "…"} → {"status", "reply", "tools", "actions"}
 *   POST /api/bakey/reset                 → empieza una conversación nueva
 *   POST /api/bakey/actions/{id}/confirm  → ejecuta una acción que propuso Bakey (paso 5.4)
 *   POST /api/bakey/actions/{id}/cancel   → la descarta
 *
 * Siempre devuelve un texto que se puede mostrar tal cual a la persona (nunca errores técnicos).
 * Charlar no se audita (no cambia datos). Las acciones confirmadas sí: el mismo código del panel las
 * registra y el modo Bakey de AuditService las marca como ASSISTANT_ACTION "(vía Bakey)".
 */
@RestController
@RequestMapping("/api/bakey")
public class BakeyRestController {

    @Autowired
    private BakeyService bakeyService;

    @Autowired
    private BakeyActionService actionService;

    @Autowired
    private UserRepository userRepository;

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
        body.put("tools", answer.tools());       // qué consultó Bakey (solo los nombres; paso 5.3)
        body.put("actions", answer.actions());   // tarjetas para Confirmar / Cancelar (paso 5.4)
        return ResponseEntity.status(httpStatusFor(answer.status())).body(body);
    }

    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> reset(HttpSession session) {
        bakeyService.reset(session);
        return ResponseEntity.ok(Map.of("status", "OK"));
    }

    // La persona tocó "Confirmar". Solo funciona si la acción es SUYA y no venció (BakeyActionService).
    @PostMapping("/actions/{id}/confirm")
    public ResponseEntity<Map<String, Object>> confirm(@PathVariable String id, Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName()).orElse(null);
        if (user == null) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        boolean admin = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ADMIN"));
        BakeyActionService.Result result = actionService.confirm(id, user, admin);
        HttpStatus http = switch (result.status()) {
            case OK -> HttpStatus.OK;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case EXPIRED -> HttpStatus.GONE;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case FAILED -> HttpStatus.CONFLICT;       // no se pudo hacer (cambió algo, falta stock…): nada se modificó
        };
        return ResponseEntity.status(http).body(Map.of("status", result.status(), "message", result.message()));
    }

    // La persona tocó "Cancelar": no cambia nada
    @PostMapping("/actions/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancel(@PathVariable String id, Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName()).orElse(null);
        boolean cancelled = user != null && actionService.cancel(id, user);
        return cancelled
                ? ResponseEntity.ok(Map.of("status", "OK", "message", "Listo, no se hizo ningún cambio."))
                : ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("status", "NOT_FOUND",
                        "message", "No encontré esa acción. Puede que ya se haya confirmado o cancelado."));
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
