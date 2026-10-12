package com.dreambake.config;

import com.dreambake.service.ReminderService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tarea automática de los recordatorios (paso 5.2): cada minuto revisa si hay recordatorios que
 * vencen dentro de la próxima hora y le manda el email al dueño (una sola vez por recordatorio).
 * Se revisa cada minuto (y no a una hora fija) porque cada recordatorio tiene su propia hora.
 */
@Component
public class ReminderScheduler {

    private final ReminderService reminderService;

    public ReminderScheduler(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    // Segundo 0 de cada minuto, hora de Argentina
    @Scheduled(cron = "0 * * * * *", zone = "America/Argentina/Buenos_Aires")
    public void sendReminderEmails() {
        reminderService.sendDueEmails();
    }
}
