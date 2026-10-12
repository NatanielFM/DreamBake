package com.dreambake.entity;

/** Resultado de una copia de seguridad (BackupRecord). */
public enum BackupStatus {
    OK,     // se creó y pasó todas las comprobaciones
    FAILED  // falló: el motivo queda en errorMessage
}
