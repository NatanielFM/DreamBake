package com.dreambake.ai;

import java.io.Serializable;
import java.util.List;

/**
 * Un mensaje de la conversación con Bakey, en un formato que no depende del proveedor.
 *
 * Hay tres tipos (role):
 *  - USER:  lo que escribió la persona (text).
 *  - MODEL: lo que contestó el modelo: un texto y/o pedidos de usar herramientas (toolCalls).
 *  - TOOL:  el resultado de una herramienta que pidió el modelo (toolResult). Se usa desde el paso 5.3.
 *
 * Es Serializable porque la conversación se guarda en la sesión del usuario (BakeyService).
 */
public record AiMessage(Role role, String text, List<AiToolCall> toolCalls, AiToolResult toolResult)
        implements Serializable {

    public enum Role { USER, MODEL, TOOL }

    public static AiMessage user(String text) {
        return new AiMessage(Role.USER, text, List.of(), null);
    }

    public static AiMessage model(AiReply reply) {
        return new AiMessage(Role.MODEL, reply.text(), reply.toolCalls(), null);
    }

    public static AiMessage tool(AiToolResult result) {
        return new AiMessage(Role.TOOL, null, List.of(), result);
    }
}
