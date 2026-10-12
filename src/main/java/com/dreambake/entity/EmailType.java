package com.dreambake.entity;

// Tipos de email que manda el sistema (se guardan en el historial, EmailLog).
// BIRTHDAY y PROMOTION son promocionales: solo se mandan a clientes con consentimiento.
public enum EmailType {
    BIRTHDAY,            // saludo y regalo de cumpleaños
    PROMOTION,           // campañas de promociones
    ORDER_RECEIVED,      // "Recibimos tu pedido" (al cliente, al hacer el pedido en la tienda)
    ORDER_CONFIRMATION,  // "Tu pedido está confirmado"
    ORDER_READY,         // "Tu pedido está listo"
    ORDER_CANCELLED,     // "Tu pedido fue cancelado"
    ORDER_REMINDER,      // recordatorio al cliente el día antes de la entrega
    NEW_ORDER_ALERT,     // "Nuevo pedido" (al negocio)
    DELIVERY_SUMMARY,    // resumen diario de entregas próximas (al negocio)
    TEST,                // email de prueba del panel
    ACCOUNT_VERIFICATION, // código de 6 números para confirmar un email (cuenta con Facebook o "Confirmá tu email")
    PASSWORD_RESET,      // recuperar contraseña
    AUDIT_ALERT,         // aviso a los administradores: el registro de auditoría fue alterado (paso 3.2)
    BACKUP_ALERT;        // aviso a los administradores: falló una copia de seguridad (paso 4.2)

    public boolean isPromotional() {
        return this == BIRTHDAY || this == PROMOTION;
    }
}
