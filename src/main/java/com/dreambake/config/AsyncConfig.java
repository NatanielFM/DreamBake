package com.dreambake.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Activa los métodos @Async: se ejecutan en otro hilo, sin hacer esperar a quien los llamó.
 * Se usa para enviar emails: hablar con el servidor de correo tarda unos segundos y
 * la pantalla no tiene que quedarse trabada mientras tanto.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
