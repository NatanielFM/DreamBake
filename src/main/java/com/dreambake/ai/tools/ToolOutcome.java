package com.dreambake.ai.tools;

import java.util.List;
import java.util.Map;

/**
 * Resultado de ejecutar una herramienta (paso 5.4).
 *
 * @param result lo que se le devuelve al MODELO (JSON chico)
 * @param card   si era una herramienta de acción, la tarjeta para la PERSONA (Confirmar / Cancelar);
 *               null en las consultas o si hubo un error
 */
public record ToolOutcome(Map<String, Object> result, ActionCard card) {

    /** Tarjeta de una acción pendiente: lo que ve la persona antes de confirmar. */
    public record ActionCard(String id, String title, List<String> lines, String expiresAt) {}

    public static ToolOutcome of(Map<String, Object> result) {
        return new ToolOutcome(result, null);
    }
}
