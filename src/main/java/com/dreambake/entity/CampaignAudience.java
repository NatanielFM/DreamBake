package com.dreambake.entity;

// A quién le llega una campaña. Siempre, además, solo a clientes que aceptaron promociones y tienen email.
public enum CampaignAudience {
    ALL_CONSENTED,        // todos los que aceptaron promociones
    TAG,                  // los de una etiqueta (Campaign.tag)
    BIRTHDAY_THIS_MONTH,  // los que cumplen años este mes
    INACTIVE_90_DAYS      // compraron alguna vez, pero no en los últimos 90 días
}
