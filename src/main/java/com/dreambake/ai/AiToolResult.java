package com.dreambake.ai;

import java.io.Serializable;
import java.util.Map;

/**
 * Resultado de una herramienta, para devolvérselo al modelo.
 *
 * @param callId id del AiToolCall al que responde
 * @param name   nombre de la herramienta
 * @param result datos que encontró o hizo la herramienta (se mandan como JSON)
 */
public record AiToolResult(String callId, String name, Map<String, Object> result) implements Serializable {
}
