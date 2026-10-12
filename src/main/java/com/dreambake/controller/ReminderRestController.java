package com.dreambake.controller;

import com.dreambake.entity.Reminder;
import com.dreambake.entity.User;
import com.dreambake.repository.UserRepository;
import com.dreambake.service.ReminderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * API de recordatorios (paso 5.2). ADMIN y PASTELERO (regla en SecurityConfig).
 *
 *   GET    /api/reminders?scope=today|pending|all  → lista (el ADMIN ve los de todo el equipo)
 *   GET    /api/reminders/alerts                   → los míos que ya llegaron a su hora (aviso del panel)
 *   POST   /api/reminders                          → crear {title, notes, dueAt: "2026-10-13T10:00", relatedOrderId}
 *   PUT    /api/reminders/{id}/done                → {done: true|false}
 *   DELETE /api/reminders/{id}
 *
 * La persona siempre se toma de la sesión (nunca de un parámetro). Los cambios se auditan en ReminderService.
 */
@RestController
@RequestMapping("/api/reminders")
public class ReminderRestController {

    @Autowired
    private ReminderService reminderService;

    @Autowired
    private UserRepository userRepository;

    /** Lo que ve el panel de cada recordatorio (sin datos del usuario más allá de su nombre). */
    public record ReminderView(Long id, String title, String notes, LocalDateTime dueAt, boolean done,
                               LocalDateTime doneAt, boolean overdue, Long relatedOrderId,
                               boolean createdByAssistant, boolean emailSent, String ownerName, boolean mine) {}

    /** Datos para crear. dueAt llega como texto ("2026-10-13T10:00") para poder dar un error claro. */
    public record CreateRequest(String title, String notes, String dueAt, Long relatedOrderId) {}

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(defaultValue = "today") String scope, Authentication auth) {
        ReminderService.Scope s;
        try {
            s = ReminderService.Scope.valueOf(scope.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Lista desconocida: usá today, pending o all."));
        }
        User me = currentUser(auth);
        return ResponseEntity.ok(views(reminderService.list(me, isAdmin(auth), s), me));
    }

    @GetMapping("/alerts")
    public ResponseEntity<List<ReminderView>> alerts(Authentication auth) {
        User me = currentUser(auth);
        return ResponseEntity.ok(views(reminderService.alerts(me), me));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody(required = false) CreateRequest body, Authentication auth) {
        if (body == null) return ResponseEntity.badRequest().body(Map.of("message", "Faltan los datos del recordatorio."));
        LocalDateTime dueAt = null;
        if (body.dueAt() != null && !body.dueAt().isBlank()) {
            try {
                dueAt = LocalDateTime.parse(body.dueAt().trim());
            } catch (DateTimeParseException e) {
                return ResponseEntity.badRequest().body(Map.of("message", "La fecha o la hora no son válidas."));
            }
        }
        User me = currentUser(auth);
        try {
            Reminder saved = reminderService.create(me, body.title(), body.notes(), dueAt, body.relatedOrderId(), false);
            return ResponseEntity.status(HttpStatus.CREATED).body(view(saved, me));
        } catch (ReminderService.InvalidReminderException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}/done")
    public ResponseEntity<?> setDone(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body,
                                     Authentication auth) {
        // Sin cuerpo = marcar como hecho (lo más común)
        boolean done = body == null || !Boolean.FALSE.equals(body.get("done"));
        User me = currentUser(auth);
        try {
            return ResponseEntity.ok(view(reminderService.setDone(id, done, me, isAdmin(auth)), me));
        } catch (ReminderService.ReminderNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, Authentication auth) {
        try {
            reminderService.delete(id, currentUser(auth), isAdmin(auth));
            return ResponseEntity.ok(Map.of("status", "OK"));
        } catch (ReminderService.ReminderNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }

    // ---------------------------------------------------------------------

    private User currentUser(Authentication auth) {
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new IllegalStateException("Usuario de la sesión no encontrado"));
    }

    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ADMIN"));
    }

    private List<ReminderView> views(List<Reminder> reminders, User me) {
        return reminders.stream().map(r -> view(r, me)).toList();
    }

    private ReminderView view(Reminder r, User me) {
        boolean overdue = !r.isDone() && r.getDueAt().isBefore(LocalDateTime.now());
        return new ReminderView(r.getId(), r.getTitle(), r.getNotes(), r.getDueAt(), r.isDone(), r.getDoneAt(),
                overdue, r.getRelatedOrderId(), Boolean.TRUE.equals(r.getCreatedByAssistant()),
                r.getEmailSentAt() != null, r.getOwner().getName(), r.getOwner().getId().equals(me.getId()));
    }
}
