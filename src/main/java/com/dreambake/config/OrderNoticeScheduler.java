package com.dreambake.config;

import com.dreambake.service.BirthdayService;
import com.dreambake.service.OrderNoticeService;
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
 * Avisos automáticos de entregas:
 *  - Todos los días a las 8:00 (hora de Argentina): resumen de entregas de los próximos 3 días
 *    para el negocio y recordatorio al cliente el día antes de su entrega.
 *  - Si la PC estaba apagada a las 8:00, al arrancar la app (después de las 8:00) se mandan los
 *    que falten. No se repiten: OrderNoticeService mira el historial de emails.
 */
@Component
public class OrderNoticeScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderNoticeScheduler.class);

    // Hora de la tarea diaria
    private static final LocalTime DAILY_TIME = LocalTime.of(8, 0);

    @Autowired
    private OrderNoticeService orderNoticeService;

    // Cron: segundo minuto hora día mes día-de-semana → "a las 8:00:00 todos los días"
    @Scheduled(cron = "0 0 8 * * *", zone = "America/Argentina/Buenos_Aires")
    public void dailyOrderNotices() {
        orderNoticeService.runDaily("tarea diaria de las 8:00");
    }

    // Al terminar de arrancar: si ya pasaron las 8:00, se ponen al día los avisos de hoy.
    // Corre en otro hilo para no demorar el arranque (mandar mails tarda unos segundos).
    @EventListener(ApplicationReadyEvent.class)
    public void catchUpAfterStartup() {
        if (LocalTime.now(BirthdayService.ZONE).isBefore(DAILY_TIME)) return;   // todavía no es la hora
        CompletableFuture.runAsync(() -> {
            try {
                orderNoticeService.runDaily("puesta al día al arrancar");
            } catch (Exception e) {
                log.error("No se pudieron mandar los avisos de entregas al arrancar: {}", e.getMessage());
            }
        });
    }
}
