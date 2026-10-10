package com.dreambake.repository;

import com.dreambake.entity.AuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Acceso a la tabla de auditoría.
 *
 * DECISIÓN DE DISEÑO: hereda de Repository y NO de JpaRepository.
 * JpaRepository (y CrudRepository) traen "de regalo" métodos como delete(), deleteById(),
 * deleteAll() y saveAll(), además de save() sobre un registro existente (que sería un UPDATE).
 * Un registro de auditoría tiene que ser de "solo agregar": si el código tuviera a mano un
 * deleteAll(), un error de programación o alguien con acceso al código podría borrar el historial
 * sin dejar rastro. Con Repository, Spring Data solo crea los métodos que declaramos acá abajo:
 * guardar uno nuevo y consultar. No existe ninguna forma de borrar desde la aplicación.
 * (La protección desde la propia base de datos, por si alguien entra directo a MySQL, es el
 * paso 3.2; y si igual lo hace, la cadena de hashes lo delata.)
 */
public interface AuditLogRepository extends Repository<AuditLog, Long> {

    // Guardar un registro NUEVO (la entidad es @Immutable, así que nunca hace un UPDATE)
    AuditLog save(AuditLog log);

    // El último registro de la cadena (su hash es el "anterior" del próximo)
    Optional<AuditLog> findTopByOrderByIdDesc();

    // Para recorrer la cadena de a tandas, en orden (verifyChain): los siguientes a un id
    List<AuditLog> findByIdGreaterThanOrderByIdAsc(Long id, Pageable page);

    long count();
}
