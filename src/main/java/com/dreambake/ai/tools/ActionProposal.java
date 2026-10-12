package com.dreambake.ai.tools;

import java.util.List;
import java.util.function.Supplier;

/**
 * Lo que Bakey PROPONE hacer (paso 5.4). Todavía no cambió nada: queda guardado como acción pendiente
 * hasta que la persona toque "Confirmar".
 *
 * @param title     título de la tarjeta (ej: "Crear pedido")
 * @param lines     el resumen legible de lo que va a pasar, una línea por dato
 * @param executor  el código que hace el cambio de verdad (se ejecuta recién al confirmar).
 *                  Devuelve el mensaje para la persona ("Listo: se creó el pedido #70") o tira
 *                  ActionFailedException con un mensaje claro si ya no se puede.
 * @param auditNote texto extra para la auditoría (ej: "motivo: se rompieron"); puede ser null
 */
public record ActionProposal(String title, List<String> lines, Supplier<String> executor, String auditNote) {

    /** La acción no se pudo hacer al confirmar (cambió algo en el medio, falta stock…). */
    public static class ActionFailedException extends RuntimeException {
        public ActionFailedException(String message) { super(message); }
    }
}
