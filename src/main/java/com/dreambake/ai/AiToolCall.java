package com.dreambake.ai;

import java.io.Serializable;
import java.util.Map;

/**
 * El modelo pide usar una herramienta.
 *
 * @param id        identificador del pedido (el resultado se devuelve con el mismo id)
 * @param name      nombre de la herramienta
 * @param args      datos que manda el modelo para la herramienta (ej: {"fecha": "2026-10-12"})
 * @param signature dato interno del proveedor que hay que devolverle tal cual en el próximo mensaje.
 *                  En Gemini es la "thoughtSignature": si no se devuelve, Gemini 3 responde error 400.
 *                  El resto del sistema no lo mira ni lo cambia.
 */
public record AiToolCall(String id, String name, Map<String, Object> args, String signature)
        implements Serializable {
}
