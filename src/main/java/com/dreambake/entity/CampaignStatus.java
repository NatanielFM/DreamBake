package com.dreambake.entity;

// Estado de una campaña de promociones
public enum CampaignStatus {
    DRAFT,      // borrador: se puede editar
    SCHEDULED,  // programada para una fecha y hora
    SENDING,    // enviándose (puede estar esperando al día siguiente por el tope diario)
    SENT,       // terminada
    CANCELLED   // cancelada
}
