package com.dreambake.entity;

/** Tipo de copia de seguridad (BackupRecord). Cada tipo se guarda en su propia carpeta. */
public enum BackupType {
    DAILY,    // diaria (tarea de las 3:00 o puesta al día al arrancar) → carpeta diarios/
    WEEKLY,   // copia semanal de un backup diario → carpeta semanales/
    MONTHLY,  // copia mensual de un backup diario → carpeta mensuales/
    MANUAL    // pedida a mano desde el panel → carpeta manuales/
}
