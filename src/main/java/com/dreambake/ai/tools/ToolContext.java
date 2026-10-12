package com.dreambake.ai.tools;

import com.dreambake.entity.User;

import java.time.LocalDate;

/**
 * Datos de quién está preguntando, para que cada herramienta sepa qué puede mostrar.
 *
 * @param user        la persona logueada
 * @param admin       true si es ADMIN (ve plata y clientes); false = PASTELERO
 * @param userMessage lo que escribió la persona (para la regla de datos de contacto, ver BakeyPrivacy)
 * @param today       la fecha de hoy en Argentina
 */
public record ToolContext(User user, boolean admin, String userMessage, LocalDate today) {
}
