package com.dreambake.config;

import com.dreambake.service.BirthdayService;
import com.dreambake.service.BusinessSettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.concurrent.CompletableFuture;

/**
 * Tareas automáticas de fidelización.
 *
 *  - Todos los días a las 9:00 (hora de Argentina) saluda a los clientes que cumplen años.
 *  - Si la PC estaba apagada a las 9:00, al arrancar la app (después de las 9:00) se mandan
 *    los saludos de hoy que falten. No se repiten: BirthdayService mira el historial de emails.
 *  - El interruptor "Saludos automáticos" de Clientes → Fidelización pausa las dos cosas.
 */
@Component
public class LoyaltyScheduler {

    private static final Logger log = LoggerFactory.getLogger(LoyaltyScheduler.class);

    // Hora de la tarea diaria
    private static final LocalTime DAILY_TIME = LocalTime.of(9, 0);

    @Autowired
    private BirthdayService birthdayService;

    @Autowired
    private BusinessSettingService settingService;

    // Cron: segundo minuto hora día mes día-de-semana → "a las 9:00:00 todos los días"
    @Scheduled(cron = "0 0 9 * * *", zone = "America/Argentina/Buenos_Aires")
    public void dailyBirthdays() {
        if (!settingService.isBirthdayEnabled()) {
            log.info("Saludos de cumpleaños pausados: la tarea de las 9:00 no hizo nada.");
            return;
        }
        birthdayService.runToday("tarea diaria de las 9:00");
    }

    // Al terminar de arrancar: si ya pasaron las 9:00, se ponen al día los saludos de hoy.
    // Corre en otro hilo para no demorar el arranque (mandar mails tarda unos segundos).
    @EventListener(ApplicationReadyEvent.class)
    public void catchUpAfterStartup() {
        if (!settingService.isBirthdayEnabled()) return;
        if (java.time.LocalTime.now(BirthdayService.ZONE).isBefore(DAILY_TIME)) return;   // todavía no es la hora
        CompletableFuture.runAsync(() -> {
            try {
                birthdayService.runToday("puesta al día al arrancar");
            } catch (Exception e) {
                log.error("No se pudieron revisar los cumpleaños al arrancar: {}", e.getMessage());
            }
        });
    }
}
