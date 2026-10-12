package com.dreambake.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Activa las tareas programadas (@Scheduled): métodos que la app ejecuta sola a cierta hora.
 * Se usa para los saludos de cumpleaños (LoyaltyScheduler), las campañas (CampaignScheduler), los
 * avisos de pedidos (OrderNoticeScheduler), la revisión de la auditoría (AuditScheduler) y los
 * backups de las 3:00 (BackupScheduler).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
