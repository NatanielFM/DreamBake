package com.dreambake.controller;

import com.dreambake.audit.Auditable;
import com.dreambake.entity.AuditAction;
import com.dreambake.entity.AuditModule;
import com.dreambake.service.AuditService;
import com.dreambake.entity.Campaign;
import com.dreambake.entity.CampaignAudience;
import com.dreambake.entity.CampaignStatus;
import com.dreambake.repository.CampaignRepository;
import com.dreambake.repository.CustomerTagRepository;
import com.dreambake.service.CampaignSender;
import com.dreambake.service.CampaignService;
import com.dreambake.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Campañas de promociones (Clientes → Fidelización → Campañas).
 * Está bajo /api/**, que en SecurityConfig es solo para ADMIN.
 *
 * El navegador manda solo TEXTO (CampaignService.Content): nunca HTML, estado ni métricas.
 */
@RestController
@RequestMapping("/api/campaigns")
public class CampaignRestController {

    @Autowired private CampaignRepository campaignRepository;
    @Autowired private AuditService audit; // registro de auditoría (paso 3.3)
    @Autowired private CustomerTagRepository tagRepository;
    @Autowired private CampaignService campaignService;
    @Autowired private CampaignSender sender;

    // ----- Lo que ve el panel -----

    // Una fila de la lista (sin el HTML enviado, que es largo)
    public record CampaignRow(Long id, String name, String subject, CampaignStatus status, String statusNote,
                              CampaignAudience audience, String tagName, String couponCode,
                              LocalDateTime scheduledAt, LocalDateTime sentAt, LocalDateTime createdAt,
                              int totalRecipients, int sentCount, int failedCount, boolean running) {}

    // Lo que se carga en el formulario para editar
    public record CampaignDetail(Long id, String name, String subject, String title, String bodyText, String imageUrl,
                                 String buttonText, String buttonUrl, CampaignAudience audience, Long tagId, Long couponId,
                                 CampaignStatus status, String statusNote, LocalDateTime scheduledAt) {}

    private CampaignRow row(Campaign c) {
        return new CampaignRow(c.getId(), c.getName(), c.getSubject(), c.getStatus(), c.getStatusNote(), c.getAudience(),
                c.getTag() == null ? null : c.getTag().getName(), c.getCoupon() == null ? null : c.getCoupon().getCode(),
                c.getScheduledAt(), c.getSentAt(), c.getCreatedAt(),
                nz(c.getTotalRecipients()), nz(c.getSentCount()), nz(c.getFailedCount()), sender.isRunning(c.getId()));
    }

    private static int nz(Integer n) { return n == null ? 0 : n; }

    private static ResponseEntity<Map<String, String>> error(String message) {
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }

    @GetMapping
    public List<CampaignRow> list() {
        return campaignRepository.findAllByOrderByCreatedAtDesc().stream().map(this::row).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<CampaignDetail> get(@PathVariable Long id) {
        return campaignRepository.findById(id).map(c -> ResponseEntity.ok(new CampaignDetail(c.getId(), c.getName(),
                c.getSubject(), c.getTitle(), c.getBodyText(), c.getImageUrl(), c.getButtonText(), c.getButtonUrl(),
                c.getAudience(), c.getTag() == null ? null : c.getTag().getId(),
                c.getCoupon() == null ? null : c.getCoupon().getId(), c.getStatus(), c.getStatusNote(), c.getScheduledAt())))
                .orElse(ResponseEntity.notFound().build());
    }

    // ----- Crear, editar, borrar -----

    @PostMapping
    @Auditable(action = AuditAction.CREATE, module = AuditModule.FIDELIZACION, entityType = "Campaña", entityId = "#body.id",
            description = "Creó la campaña de emails «#{#body.name}»")
    public ResponseEntity<?> create(@RequestBody CampaignService.Content content, Authentication auth) {
        Campaign c = new Campaign();
        String problem = campaignService.apply(c, content);
        if (problem != null) return error(problem);
        c.setCreatedBy(auth.getName());
        return ResponseEntity.ok(row(campaignRepository.save(c)));
    }

    // Solo se editan las que todavía no empezaron (borrador o programada)
    @PutMapping("/{id}")
    @Auditable(action = AuditAction.UPDATE, module = AuditModule.FIDELIZACION, entityType = "Campaña", entityId = "#id",
            description = "Editó la campaña de emails «#{#body.name}»")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody CampaignService.Content content) {
        Campaign c = campaignRepository.findById(id).orElse(null);
        if (c == null) return ResponseEntity.notFound().build();
        if (c.getStatus() != CampaignStatus.DRAFT && c.getStatus() != CampaignStatus.SCHEDULED) {
            return error("Esta campaña ya se envió o se está enviando: no se puede cambiar.");
        }
        String problem = campaignService.apply(c, content);
        if (problem != null) return error(problem);
        c.setStatusNote(null);
        return ResponseEntity.ok(row(campaignRepository.save(c)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        Campaign c = campaignRepository.findById(id).orElse(null);
        if (c == null) return ResponseEntity.notFound().build();
        if (c.getStatus() != CampaignStatus.DRAFT && c.getStatus() != CampaignStatus.CANCELLED) {
            return error("Solo se pueden borrar los borradores y las campañas canceladas.");
        }
        campaignRepository.delete(c);
        audit.log(AuditAction.DELETE, AuditModule.FIDELIZACION, "Campaña", id, "Eliminó la campaña de emails «" + c.getName() + "»", null);
        return ResponseEntity.ok().build();
    }

    // ----- Ayudas del formulario -----

    // ¿A cuántos clientes le llega esta audiencia?
    @GetMapping("/audience-count")
    public Map<String, Integer> audienceCount(@RequestParam CampaignAudience audience,
                                              @RequestParam(required = false) Long tagId) {
        var tag = tagId == null ? null : tagRepository.findById(tagId).orElse(null);
        return Map.of("count", campaignService.audience(audience, tag).size());
    }

    // Vista previa: el mismo HTML que se va a mandar (con lo que está escrito en el formulario, sin guardar)
    @PostMapping("/preview")
    public ResponseEntity<?> preview(@RequestBody CampaignService.Content content) {
        Campaign c = new Campaign();
        String problem = campaignService.apply(c, content);
        if (problem != null) return error(problem);
        return ResponseEntity.ok(Map.of("html", campaignService.previewHtml(c)));
    }

    // Vista previa de una campaña guardada (por ejemplo, una que ya se envió)
    @GetMapping("/{id}/preview")
    public ResponseEntity<?> previewSaved(@PathVariable Long id) {
        return campaignRepository.findById(id)
                .<ResponseEntity<?>>map(c -> ResponseEntity.ok(Map.of("html", campaignService.previewHtml(c))))
                .orElse(ResponseEntity.notFound().build());
    }

    // ----- Acciones -----

    // "Enviar prueba a mi email": solo al administrador que tiene la sesión iniciada
    @PostMapping("/{id}/test")
    public ResponseEntity<?> test(@PathVariable Long id, Authentication auth) {
        Campaign c = campaignRepository.findById(id).orElse(null);
        if (c == null) return ResponseEntity.notFound().build();
        try {
            EmailService.Resultado r = campaignService.sendTest(c, auth.getName());
            return r.ok() ? ResponseEntity.ok(Map.of("message", "Te mandamos la prueba a " + auth.getName() + "."))
                    : error(r.mensaje());
        } catch (Exception e) {
            return error("El servidor de correo tardó demasiado en responder. Probá de nuevo en unos minutos.");
        }
    }

    public record ScheduleRequest(LocalDateTime scheduledAt) {}

    // "Programar": la tarea de cada 5 minutos (CampaignScheduler) la manda cuando llega la hora
    @PostMapping("/{id}/schedule")
    @Auditable(action = AuditAction.STATUS_CHANGE, module = AuditModule.FIDELIZACION, entityType = "Campaña", entityId = "#id",
            description = "Programó la campaña «#{#body.name}» para el #{#req.scheduledAt().format(T(java.time.format.DateTimeFormatter).ofPattern('dd/MM/yyyy HH:mm'))}")
    public ResponseEntity<?> schedule(@PathVariable Long id, @RequestBody ScheduleRequest req) {
        Campaign c = campaignRepository.findById(id).orElse(null);
        if (c == null) return ResponseEntity.notFound().build();
        if (c.getStatus() != CampaignStatus.DRAFT && c.getStatus() != CampaignStatus.SCHEDULED) {
            return error("Esta campaña ya se envió o se está enviando.");
        }
        if (req == null || req.scheduledAt() == null || !req.scheduledAt().isAfter(LocalDateTime.now())) {
            return error("Elegí una fecha y hora que todavía no haya pasado.");
        }
        String problem = campaignService.validateForSend(c, false);
        if (problem != null) return error(problem);
        c.setScheduledAt(req.scheduledAt());
        c.setStatus(CampaignStatus.SCHEDULED);
        c.setStatusNote(null);
        return ResponseEntity.ok(row(campaignRepository.save(c)));
    }

    // "Enviar ahora" (el panel ya pidió confirmación mostrando la cantidad de destinatarios)
    @PostMapping("/{id}/send")
    public ResponseEntity<?> send(@PathVariable Long id) {
        Campaign c = campaignRepository.findById(id).orElse(null);
        if (c == null) return ResponseEntity.notFound().build();
        if (c.getStatus() != CampaignStatus.DRAFT && c.getStatus() != CampaignStatus.SCHEDULED) {
            return error("Esta campaña ya se envió o se está enviando.");
        }
        String problem = campaignService.validateForSend(c, true);
        if (problem != null) return error(problem);
        campaignService.start(c);
        return ResponseEntity.ok(row(c));
    }

    // Cancelar una programada o una que se está enviando (se detiene antes del próximo email)
    @PostMapping("/{id}/cancel")
    @Auditable(action = AuditAction.STATUS_CHANGE, module = AuditModule.FIDELIZACION, entityType = "Campaña", entityId = "#id",
            description = "Canceló la campaña «#{#body.name}»")
    public ResponseEntity<?> cancel(@PathVariable Long id) {
        Campaign c = campaignRepository.findById(id).orElse(null);
        if (c == null) return ResponseEntity.notFound().build();
        // UPDATE puntual (y no save()): así no se pisan los contadores que el envío actualiza a la vez
        if (campaignRepository.cancel(id) == 0) {
            return error("Solo se pueden cancelar las campañas programadas o que se están enviando.");
        }
        return ResponseEntity.ok(row(campaignRepository.findById(id).orElseThrow()));
    }
}
