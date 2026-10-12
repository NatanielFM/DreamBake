package com.dreambake.repository;

import com.dreambake.entity.BackupRecord;
import com.dreambake.entity.BackupStatus;
import com.dreambake.entity.BackupType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BackupRecordRepository extends JpaRepository<BackupRecord, Long> {
    // Último backup con ese resultado, de cualquier tipo (¿hace cuánto que no hay uno bueno?)
    Optional<BackupRecord> findTopByStatusOrderByCreatedAtDesc(BackupStatus status);

    // Último backup de un tipo con ese resultado (¿cuándo se hizo la última copia semanal?)
    Optional<BackupRecord> findTopByTypeAndStatusOrderByCreatedAtDesc(BackupType type, BackupStatus status);

    // Último intento, haya salido bien o mal (panel de backups, paso 4.3)
    Optional<BackupRecord> findTopByOrderByCreatedAtDescIdDesc();

    // Todos los backups con ese resultado, del más nuevo al más viejo (lista del panel)
    List<BackupRecord> findByStatusOrderByCreatedAtDescIdDesc(BackupStatus status);
}
