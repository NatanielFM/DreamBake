package com.dreambake.entity;

// Tipos de email que manda el sistema (se guardan en el historial, EmailLog).
// BIRTHDAY y PROMOTION son promocionales: solo se mandan a clientes con consentimiento.
public enum EmailType {
    BIRTHDAY,            // saludo y regalo de cumpleaños
    PROMOTION,           // campañas de promociones
    ORDER_REMINDER,      // recordatorio de un pedido
    ORDER_CONFIRMATION,  // aviso de pedido confirmado
    TEST,                // email de prueba del panel
    PASSWORD_RESET;      // recuperar contraseña

    public boolean isPromotional() {
        return this == BIRTHDAY || this == PROMOTION;
    }
}
