package com.dreambake.controller;

import com.dreambake.entity.BackupRecord;
import com.dreambake.entity.BackupStatus;
import com.dreambake.entity.BackupType;
import com.dreambake.service.BackupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * API de copias de seguridad (paso 4.1). Solo ADMIN (regla general de /api/** en SecurityConfig).
 * Por ahora solo permite hacer un backup en el momento; el panel completo llega en el paso 4.3.
 * La auditoría la registra BackupService (salga bien o mal).
 */
@RestController
@RequestMapping("/api/backups")
public class BackupRestController {

    @Autowired
    private BackupService backupService;

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
}
