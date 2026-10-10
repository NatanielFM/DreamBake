package com.dreambake.entity;

/** Qué tipo de acción quedó registrada en la auditoría (AuditLog). */
public enum AuditAction {
    CREATE,           // alta (crear un pedido, un producto, un cupón…)
    UPDATE,           // modificación
    DELETE,           // baja
    STATUS_CHANGE,    // cambio de estado (por ejemplo, confirmar un pedido)
    LOGIN,            // inicio de sesión correcto
    LOGIN_FAILED,     // intento de inicio de sesión fallido
    LOGOUT,           // cierre de sesión
    EXPORT,           // exportar datos (por ejemplo, a Excel)
    DOWNLOAD,         // descargar un archivo (por ejemplo, un recibo)
    CONFIG_CHANGE,    // cambio de configuración (Ajustes)
    EMAIL_CAMPAIGN,   // envío de una campaña de emails
    BACKUP,           // copia de seguridad
    ASSISTANT_ACTION  // acción hecha por Bakey, el asistente de IA
}
