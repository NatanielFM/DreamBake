package com.dreambake.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Activa las tareas programadas (@Scheduled): métodos que la app ejecuta sola a cierta hora.
 * Hoy se usa para los saludos de cumpleaños (ver LoyaltyScheduler); los backups y recordatorios
 * de los próximos pasos usan lo mismo.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
