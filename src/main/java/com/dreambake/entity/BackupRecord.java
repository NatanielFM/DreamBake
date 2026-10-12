package com.dreambake.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Historial de copias de seguridad (paso 4.1): una fila por cada backup, salga bien o mal.
 * Las copias semanales y mensuales tienen su propia fila (mismo hash y tamaño que el diario
 * del que salieron), así el panel del paso 4.3 puede listar qué hay en cada carpeta.
 */
@Entity
@Table(name = "backup_records")
@Getter
@Setter
public class BackupRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Nombre del archivo .zip (ej: dreambake_2026-10-11_0300.zip). Vacío si falló antes de crearlo.
    private String fileName;

    // Tipo y estado se guardan como texto (VARCHAR) y no como ENUM de MySQL: así se pueden agregar
    // valores nuevos sin tocar la base (lección del paso 1.5).
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 20)
    private BackupType type;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 20)
    private BackupStatus status;

    // Tamaño del .zip en bytes
    private Long sizeBytes;

    // Huella SHA-256 del .zip (64 caracteres hexadecimales). Sirve para comprobar que no se dañó.
    @Column(length = 64)
    private String sha256;

    // Por qué falló (vacío si salió bien)
    @Column(length = 500)
    private String errorMessage;

    // Cuánto tardó, en milisegundos
    private Long durationMs;

    private LocalDateTime createdAt;

    // Valor por defecto al guardar por primera vez
    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
