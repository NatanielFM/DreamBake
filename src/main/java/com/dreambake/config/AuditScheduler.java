package com.dreambake.config;

import com.dreambake.service.AuditIntegrityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tarea semanal (paso 3.2): todos los lunes a las 7:00 revisa que el registro de auditoría no haya
 * sido alterado (AuditIntegrityService). Si lo fue, manda un email a los administradores y deja
 * un aviso en el panel. También se puede revisar en el momento con POST /api/audit/verify.
 */
@Component
public class AuditScheduler {

    @Autowired
    private AuditIntegrityService integrityService;

    // Cron: segundo minuto hora día mes día-de-semana → "lunes a las 7:00:00"
    @Scheduled(cron = "0 0 7 * * MON", zone = "America/Argentina/Buenos_Aires")
    public void weeklyCheck() {
        integrityService.check("revisión semanal de los lunes");
    }
}
