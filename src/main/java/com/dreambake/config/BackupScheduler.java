package com.dreambake.config;

import com.dreambake.entity.BackupType;
import com.dreambake.service.BackupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Copias de seguridad automáticas (paso 4.1).
 *
 *  - Todos los días a las 3:00 (hora de Argentina) se hace un backup diario (BackupService).
 *  - Si la PC estuvo apagada a esa hora: al arrancar la app, si el último backup bueno tiene más
 *    de 24 horas (o nunca se hizo uno), se hace uno a los 2 minutos de iniciar. Se espera un poco
 *    para no cargar la PC justo mientras arranca todo lo demás.
 */
@Component
public class BackupScheduler {

    private static final Logger log = LoggerFactory.getLogger(BackupScheduler.class);

    // Cuánto se espera después de arrancar para hacer el backup atrasado
    private static final long CATCH_UP_DELAY_MINUTES = 2;

    @Autowired
    private BackupService backupService;

    // El programador de tareas de Spring (lo crea @EnableScheduling de SchedulingConfig)
    @Autowired
    private TaskScheduler taskScheduler;

    // Cron: segundo minuto hora día mes día-de-semana → "a las 3:00:00 todos los días"
    @Scheduled(cron = "0 0 3 * * *", zone = "America/Argentina/Buenos_Aires")
    public void dailyBackup() {
        backupService.runBackup(BackupType.DAILY, "tarea diaria de las 3:00");
    }

    // Al terminar de arrancar: ¿hace falta ponerse al día?
    @EventListener(ApplicationReadyEvent.class)
    public void catchUpAfterStartup() {
        if (!backupService.isConfigured()) return;   // ConfigurationCheck ya avisa en el log
        LocalDateTime limit = LocalDateTime.now().minusHours(24);
        boolean recent = backupService.lastSuccessful()
                .map(r -> r.getCreatedAt().isAfter(limit))
                .orElse(false);
        if (recent) return;

        log.info("El último backup bueno tiene más de 24 horas (o no hay ninguno): se hace uno en {} minutos.",
                CATCH_UP_DELAY_MINUTES);
        // Se agenda en el TaskScheduler de Spring (el mismo de las tareas @Scheduled), sin frenar el arranque.
        // Importante: si la app se apaga o se reinicia antes de los 2 minutos, Spring cancela esta tarea.
        // (Con un temporizador "suelto" la tarea sobrevivía al reinicio y quedaba un zip sin registrar.)
        taskScheduler.schedule(() -> {
            try {
                backupService.runBackup(BackupType.DAILY, "puesta al día al arrancar");
            } catch (Exception e) {
                log.error("No se pudo hacer el backup atrasado: {}", e.getMessage());
            }
        }, Instant.now().plus(Duration.ofMinutes(CATCH_UP_DELAY_MINUTES)));
    }
}
