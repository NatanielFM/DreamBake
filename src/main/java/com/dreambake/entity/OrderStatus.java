package com.dreambake.entity;

public enum OrderStatus {
    PENDING, // Pendiente (El cliente lo armó en la web pero no pagó)
    CONFIRMED, // Confirmado (Tu amiga recibió la seña)
    IN_PROGRESS, // En Preparación (Tu amiga está cocinando)
    COMPLETED, // Completado (Entregado y pagado al 100%)
    CANCELLED // Cancelado
}