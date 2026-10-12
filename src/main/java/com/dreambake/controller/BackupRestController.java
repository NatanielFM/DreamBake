package com.dreambake.controller;

import com.dreambake.entity.AuditAction;
import com.dreambake.entity.AuditModule;
import com.dreambake.entity.BackupRecord;
import com.dreambake.entity.BackupStatus;
import com.dreambake.entity.BackupType;
import com.dreambake.repository.BackupRecordRepository;
import com.dreambake.service.AuditService;
import com.dreambake.service.BackupService;
import com.dreambake.service.RestoreService;
import com.dreambake.util.ClientIpResolver;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * API de copias de seguridad (pasos 4.1 y 4.3). Solo ADMIN (regla general de /api/** en SecurityConfig).
 *
 *   GET  /api/backups                → estado, espacio y lista (pestaña "Backups" del panel)
 *   POST /api/backups/run            → hacer un backup ahora
 *   GET  /api/backups/{id}/download  → descargar el zip (queda en la auditoría)
 *   POST /api/backups/{id}/restore   → restaurar (contraseña + RESTAURAR + "Entiendo"; ver RestoreService)
 */
@RestController
@RequestMapping("/api/backups")
public class BackupRestController {

    @Autowired
    private BackupService backupService;

    @Autowired
    private RestoreService restoreService;

    @Autowired
    private BackupRecordRepository repository;

    @Autowired
    private AuditService auditService;

    // Estado general + lista de backups disponibles
    @GetMapping
    public ResponseEntity<Map<String, Object>> overview() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("configured", backupService.isConfigured());
        body.put("warning", backupService.staleWarning());
        body.put("lastBackup", backupService.lastSuccessful().map(this::summary).orElse(null));
        body.put("lastAttempt", backupService.lastAttempt().map(this::summary).orElse(null));
        BackupService.Storage storage = backupService.storage();
        body.put("usedBytes", storage.usedBytes());
        body.put("freeBytes", storage.freeBytes());
        body.put("backups", backupService.list().stream().map(this::summary).toList());
        return ResponseEntity.ok(body);
    }

    // Hace un backup manual ahora (carpeta manuales/). Puede tardar unos segundos.
    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> runNow() {
        BackupService.Outcome outcome = backupService.runBackup(BackupType.MANUAL, "pedido desde el panel");

        Map<String, Object> body = new LinkedHashMap<>();
        if (!outcome.ran()) {
            // No se intentó: falta configurar (400) o ya hay uno en curso (409)
            body.put("error", outcome.message());
            int status = backupService.isConfigured() ? 409 : 400;
            return ResponseEntity.status(status).body(body);
        }

        BackupRecord r = outcome.record();
        body.put("id", r.getId());
        body.put("status", r.getStatus());
        body.put("fileName", r.getFileName());
        body.put("sizeBytes", r.getSizeBytes());
        body.put("sha256", r.getSha256());
        body.put("durationMs", r.getDurationMs());
        body.put("error", r.getErrorMessage());
        // Si falló, 500 con el motivo (que ya quedó guardado en backup_records y en la auditoría)
        return r.getStatus() == BackupStatus.OK ? ResponseEntity.ok(body) : ResponseEntity.status(500).body(body);
    }

    // Descarga el zip. El archivo tiene TODOS los datos (y la clave del correo): queda registrado quién lo bajó.
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        BackupRecord r = repository.findById(id).filter(x -> x.getStatus() == BackupStatus.OK).orElse(null);
        Path file = r == null ? null : backupService.fileOf(r).filter(Files::isRegularFile).orElse(null);
        if (file == null) return ResponseEntity.notFound().build();

        auditService.log(AuditAction.DOWNLOAD, AuditModule.SISTEMA, "Backup", r.getId(),
                "Descargó la copia de seguridad " + r.getFileName(),
                Map.of("archivo", r.getFileName(), "tipo", typeLabel(r.getType())));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(r.getFileName()).build().toString())
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(new FileSystemResource(file));
    }

    /** Lo que manda la ventana de restaurar. */
    public record RestoreRequest(String password, String confirm, Boolean understood) {}

    // Restaurar. Todas las comprobaciones (y la auditoría) están en RestoreService.
    @PostMapping("/{id}/restore")
    public ResponseEntity<Map<String, Object>> restore(@PathVariable Long id, @RequestBody RestoreRequest req,
                                                       Authentication authentication,
                                                       HttpServletRequest request, HttpServletResponse response) {
        RestoreService.Result result = restoreService.restore(id, req.password(), req.confirm(),
                Boolean.TRUE.equals(req.understood()), authentication.getName(), ClientIpResolver.resolve(request));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put(result.ok() ? "message" : "error", result.message());
        body.put("warnings", result.warnings());
        if (result.ok()) {
            // La tabla de usuarios pudo cambiar: se cierra la sesión de quien restauró (y su "recordarme")
            // y el panel lo manda a ingresar de nuevo
            HttpSession session = request.getSession(false);
            if (session != null) session.invalidate();
            SecurityContextHolder.clearContext();
            Cookie rememberMe = new Cookie("remember-me", "");
            rememberMe.setMaxAge(0);
            rememberMe.setPath("/");
            response.addCookie(rememberMe);
            body.put("redirect", "/login?restored");
        }
        return ResponseEntity.status(result.status()).body(body);
    }

    // ===================== Ayudas =====================

    private Map<String, Object> summary(BackupRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("type", r.getType());
        m.put("typeLabel", typeLabel(r.getType()));
        m.put("status", r.getStatus());
        m.put("fileName", r.getFileName());
        m.put("sizeBytes", r.getSizeBytes());
        m.put("createdAt", r.getCreatedAt());
        m.put("error", r.getErrorMessage());
        return m;
    }

    // Nombre para el panel (con mayúscula: "Diaria", "Antes de restaurar"…)
    private static String typeLabel(BackupType type) {
        if (type == null) return "";
        return switch (type) {
            case DAILY -> "Diaria";
            case WEEKLY -> "Semanal";
            case MONTHLY -> "Mensual";
            case MANUAL -> "Manual";
            case PRE_RESTORE -> "Antes de restaurar";
        };
    }
}
