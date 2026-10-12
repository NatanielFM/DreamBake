package com.dreambake.ai;

import java.util.List;

/**
 * Respuesta del modelo: un texto para mostrar, pedidos de usar herramientas, o las dos cosas.
 * Si hasToolCalls() es true, hay que ejecutar las herramientas y volver a llamar al modelo
 * con los resultados (lo hace BakeyService desde el paso 5.3).
 */
public record AiReply(String text, List<AiToolCall> toolCalls) {

    public AiReply {
        text = text == null ? "" : text;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }
}
