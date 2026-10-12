package com.dreambake.service;

import com.dreambake.audit.AuditText;
import com.dreambake.config.AppProperties;
import com.dreambake.entity.AuditAction;
import com.dreambake.entity.AuditModule;
import com.dreambake.entity.EmailType;
import com.dreambake.entity.Reminder;
import com.dreambake.entity.User;
import com.dreambake.repository.OrderRepository;
import com.dreambake.repository.ReminderRepository;
import com.dreambake.util.InputSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Recordatorios del equipo (paso 5.2). Lo usan el panel (ReminderRestController) y, desde el paso 5.4,
 * Bakey (con byAssistant = true).
 *
 * Reglas:
 *  - Cada persona ve y maneja sus recordatorios. El ADMIN ve los de todo el equipo y también puede
 *    marcarlos hechos o borrarlos, pero solo crea para sí mismo.
 *  - Si alguien intenta tocar uno ajeno sin ser ADMIN, se responde "no existe" (no se revela nada).
 *  - Una hora antes se manda un email al dueño (sendDueEmails, lo llama ReminderScheduler cada minuto).
 *  - Todo cambio queda en la auditoría (módulo Recordatorios).
 *
 * La hora es la de Argentina: la zona horaria de la app está fija en DreambakeApplication.main.
 */
@Service
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);

    public static final int MAX_TITLE = 120;
    public static final int MAX_NOTES = 1000;

    private static final Locale AR = Locale.forLanguageTag("es-AR");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", AR);

    /** Error de datos (fecha pasada, falta el título…): el mensaje se le muestra a la persona. */
    public static class InvalidReminderException extends RuntimeException {
        public InvalidReminderException(String message) { super(message); }
    }

    /** El recordatorio no existe o no es de esta persona. */
    public static class ReminderNotFoundException extends RuntimeException {
        public ReminderNotFoundException() { super("No se encontró el recordatorio"); }
    }

    private final ReminderRepository repository;
    private final OrderRepository orderRepository;
    private final AuditService auditService;
    private final EmailService emailService;
    private final AppProperties appProperties;

    // Para que la tarea del email nunca mande dos veces el mismo aviso
    private final ReentrantLock emailLock = new ReentrantLock();
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();

    public ReminderService(ReminderRepository repository, OrderRepository orderRepository, AuditService auditService,
                           EmailService emailService, AppProperties appProperties) {
        this.repository = repository;
        this.orderRepository = orderRepository;
        this.auditService = auditService;
        this.emailService = emailService;
        this.appProperties = appProperties;
    }

    // ---------------------------------------------------------------------
    // Crear, listar, marcar hecho y borrar
    // ---------------------------------------------------------------------

    /** Datos ya revisados y limpios, listos para guardar. */
    public record ValidReminder(String title, String notes, LocalDateTime dueAt, Long relatedOrderId) {}

    /**
     * Revisa y limpia los datos de un recordatorio SIN guardarlo. Lo usa create() y también Bakey
     * (paso 5.4) para avisar un error antes de proponer la acción.
     */
    public ValidReminder validate(String title, String notes, LocalDateTime dueAt, Long relatedOrderId) {
        String cleanTitle = InputSanitizer.clean(title, MAX_TITLE);
        if (cleanTitle == null || cleanTitle.isBlank()) {
            throw new InvalidReminderException("Escribí qué hay que hacer.");
        }
        String cleanNotes = InputSanitizer.clean(notes, MAX_NOTES);
        if (cleanNotes != null && cleanNotes.isBlank()) cleanNotes = null;

        if (dueAt == null) {
            throw new InvalidReminderException("Elegí la fecha y la hora del recordatorio.");
        }
        LocalDateTime now = LocalDateTime.now();
        // Un minuto de tolerancia: si la persona elige "ahora", no se rechaza por unos segundos
        if (dueAt.isBefore(now.minusMinutes(1))) {
            throw new InvalidReminderException("Esa fecha y hora ya pasaron. Elegí una que todavía no haya llegado.");
        }
        if (dueAt.isAfter(now.plusYears(2))) {
            throw new InvalidReminderException("La fecha está demasiado lejos (máximo 2 años).");
        }
        if (relatedOrderId != null && !orderRepository.existsById(relatedOrderId)) {
            throw new InvalidReminderException("No existe el pedido N° " + relatedOrderId + ".");
        }
        return new ValidReminder(cleanTitle, cleanNotes, dueAt.withSecond(0).withNano(0), relatedOrderId);
    }

    /**
     * Crea un recordatorio para "owner".
     * @param byAssistant true si lo pidió Bakey (queda marcado; la auditoría lo anota como "vía Bakey"
     *                    porque la acción confirmada corre en el modo Bakey de AuditService)
     */
    public Reminder create(User owner, String title, String notes, LocalDateTime dueAt, Long relatedOrderId,
                           boolean byAssistant) {
        ValidReminder valid = validate(title, notes, dueAt, relatedOrderId);

        Reminder r = new Reminder();
        r.setOwner(owner);
        r.setTitle(valid.title());
        r.setNotes(valid.notes());
        r.setDueAt(valid.dueAt());
        r.setRelatedOrderId(valid.relatedOrderId());
        r.setCreatedByAssistant(byAssistant);
        Reminder saved = repository.save(r);

        auditService.log(AuditAction.CREATE, AuditModule.RECORDATORIOS, "Recordatorio", saved.getId(),
                "Creó el recordatorio «" + saved.getTitle() + "» para el " + saved.getDueAt().format(DATE_TIME),
                AuditText.change(null, snapshot(saved)));
        return saved;
    }

    /** Qué recordatorios mostrar: hoy (hoy + vencidos + hechos hoy), pendientes, o todos (últimos 200). */
    public enum Scope { TODAY, PENDING, ALL }

    /** Lista para la persona. El ADMIN ve los de todo el equipo; los demás, solo los suyos. */
    public List<Reminder> list(User user, boolean isAdmin, Scope scope) {
        User owner = isAdmin ? null : user;   // null = de todos
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        return switch (scope) {
            case TODAY -> {
                List<Reminder> result = new ArrayList<>(repository.findPendingUntil(owner, startOfToday.plusDays(1)));
                result.addAll(repository.findDoneSince(owner, startOfToday));
                yield result;
            }
            case PENDING -> repository.findPending(owner);
            case ALL -> repository.findLatest(owner, PageRequest.of(0, 200));
        };
    }

    /** Los de esta persona que ya llegaron a su hora y no están hechos (para el aviso del panel). */
    public List<Reminder> alerts(User user) {
        return repository.findByOwnerAndDoneFalseAndDueAtLessThanEqualOrderByDueAt(user, LocalDateTime.now());
    }

    /** Marca como hecho (o vuelve a dejar pendiente, por si se tocó sin querer). */
    public Reminder setDone(Long id, boolean done, User user, boolean isAdmin) {
        Reminder r = findAllowed(id, user, isAdmin);
        if (r.isDone() == done) return r;   // ya estaba así: no se registra nada

        LocalDateTime doneAt = done ? LocalDateTime.now() : null;
        repository.updateDone(id, done, doneAt);
        r.setDone(done);
        r.setDoneAt(doneAt);

        auditService.log(AuditAction.STATUS_CHANGE, AuditModule.RECORDATORIOS, "Recordatorio", id,
                (done ? "Marcó como hecho el recordatorio «" : "Volvió a dejar pendiente el recordatorio «")
                        + r.getTitle() + "»" + ofWhom(r, user),
                AuditText.change(Map.of("hecho", !done), Map.of("hecho", done)));
        return r;
    }

    public void delete(Long id, User user, boolean isAdmin) {
        Reminder r = findAllowed(id, user, isAdmin);
        Map<String, Object> before = snapshot(r);
        repository.delete(r);
        auditService.log(AuditAction.DELETE, AuditModule.RECORDATORIOS, "Recordatorio", id,
                "Borró el recordatorio «" + r.getTitle() + "»" + ofWhom(r, user),
                AuditText.change(before, null));
    }

    // Busca el recordatorio y revisa que la persona lo pueda tocar (el dueño o un ADMIN).
    // Público para que Bakey (paso 5.4) lo revise antes de proponer "marcar como hecho".
    public Reminder findAllowed(Long id, User user, boolean isAdmin) {
        Reminder r = repository.findById(id).orElseThrow(ReminderNotFoundException::new);
        if (!isAdmin && !r.getOwner().getId().equals(user.getId())) {
            throw new ReminderNotFoundException();
        }
        return r;
    }

    // " de Juan" cuando el ADMIN toca un recordatorio de otra persona
    private String ofWhom(Reminder r, User user) {
        return r.getOwner().getId().equals(user.getId()) ? "" : " de " + r.getOwner().getName();
    }

    // Datos del recordatorio que se guardan en la auditoría
    private Map<String, Object> snapshot(Reminder r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("titulo", r.getTitle());
        if (r.getNotes() != null) m.put("notas", r.getNotes());
        m.put("vence", r.getDueAt().format(DATE_TIME));
        if (r.getRelatedOrderId() != null) m.put("pedido", r.getRelatedOrderId());
        m.put("hecho", r.isDone());
        m.put("dueno", r.getOwner().getName());
        m.put("creadoPorBakey", Boolean.TRUE.equals(r.getCreatedByAssistant()));
        return m;
    }

    // ---------------------------------------------------------------------
    // Email "falta 1 hora"
    // ---------------------------------------------------------------------

    /**
     * Manda el email a los recordatorios que vencen dentro de la próxima hora y todavía no lo recibieron.
     * Lo llama ReminderScheduler cada minuto. Si se crea uno con menos de una hora de anticipación,
     * el email sale en la vuelta siguiente; los que ya vencieron no se mandan.
     * El email queda en el historial de emails (email_logs), como los demás automáticos.
     */
    public void sendDueEmails() {
        if (!emailService.estaConfigurado()) {
            log.debug("Recordatorios: no hay cuenta de correo configurada, no se mandan emails");
            return;
        }
        // Si la vuelta anterior sigue mandando (correo lento), esta se saltea
        if (!emailLock.tryLock()) return;
        try {
            LocalDateTime now = LocalDateTime.now();
            for (Reminder r : repository.findPendingForEmail(now, now.plusHours(1))) {
                sendOne(r);
            }
        } finally {
            emailLock.unlock();
        }
    }

    private void sendOne(Reminder r) {
        String to = r.getOwner().getEmail();
        if (to == null || to.isBlank() || !inFlight.add(r.getId())) return;
        try {
            // Se vuelve a leer por si en el medio lo marcaron hecho o ya se mandó
            Reminder fresh = repository.findById(r.getId()).orElse(null);
            if (fresh == null || fresh.isDone() || fresh.getEmailSentAt() != null) return;

            String subject = "Recordatorio: " + fresh.getTitle() + " (a las " + fresh.getDueAt().format(HOUR) + ")";
            boolean isAdmin = fresh.getOwner().getRoles() != null && fresh.getOwner().getRoles().contains("ADMIN");
            Map<String, Object> vars = new LinkedHashMap<>();
            vars.put("nombre", firstName(fresh.getOwner().getName()));
            vars.put("titulo", fresh.getTitle());
            vars.put("cuando", whenText(fresh.getDueAt()));
            vars.put("notas", fresh.getNotes());
            vars.put("pedido", fresh.getRelatedOrderId());
            vars.put("panelUrl", appProperties.getPublicUrl() + (isAdmin ? "/admin" : "/baker"));

            EmailService.Resultado result = emailService.enviar(to, subject, "email/recordatorio-tarea", vars,
                    EmailType.TASK_REMINDER, null, null).get(30, TimeUnit.SECONDS);
            if (result.ok()) {
                repository.markEmailSent(fresh.getId(), LocalDateTime.now());
            } else {
                log.warn("Recordatorios: no se pudo mandar el email del recordatorio #{}: {}", fresh.getId(), result.mensaje());
            }
        } catch (Exception e) {
            log.warn("Recordatorios: error al mandar el email del recordatorio #{}", r.getId(), e);
        } finally {
            inFlight.remove(r.getId());
        }
    }

    // "hoy a las 10:00" / "mañana a las 10:00" / "lunes 13 de octubre a las 10:00"
    static String whenText(LocalDateTime dueAt) {
        LocalDate today = LocalDate.now();
        String hour = " a las " + dueAt.format(HOUR);
        if (dueAt.toLocalDate().equals(today)) return "hoy" + hour;
        if (dueAt.toLocalDate().equals(today.plusDays(1))) return "mañana" + hour;
        return dueAt.format(DAY) + hour;
    }

    private static String firstName(String name) {
        if (name == null || name.isBlank()) return "";
        return name.trim().split("\\s+")[0];
    }
}
