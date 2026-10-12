package com.dreambake.ai;

/**
 * Error al hablar con el proveedor de IA. El "kind" dice qué pasó, y con eso BakeyService
 * elige un mensaje amable para la persona. El mensaje técnico (getMessage) es solo para el log:
 * nunca se manda al navegador.
 */
public class AiException extends RuntimeException {

    public enum Kind {
        QUOTA,           // el proveedor dice que se agotó la cuota (HTTP 429)
        TIMEOUT,         // tardó más de lo permitido
        NOT_CONFIGURED,  // falta la clave
        BLOCKED,         // el proveedor no quiso responder (filtros de seguridad)
        FAILED           // cualquier otro problema (clave inválida, sin internet, error del servidor…)
    }

    private final Kind kind;

    public AiException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public AiException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }
}
