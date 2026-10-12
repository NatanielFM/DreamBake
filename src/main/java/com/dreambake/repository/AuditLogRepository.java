package com.dreambake.repository;

import com.dreambake.entity.AuditAction;
import com.dreambake.entity.AuditLog;
import com.dreambake.entity.AuditModule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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

    /**
     * Búsqueda con filtros para la pestaña "Registro de actividad" (paso 3.4). Cada filtro es
     * opcional: si viene null, no filtra. Los más recientes primero. "to" no se incluye (es el día
     * siguiente al "hasta" que eligió el usuario). text tiene que venir en minúsculas y con % (LIKE).
     * (No se usa JpaSpecificationExecutor: trae un método delete(), y acá no tiene que existir ninguno.)
     */
    @Query(value = "SELECT a FROM AuditLog a WHERE " + FILTERS + " ORDER BY a.id DESC",
           countQuery = "SELECT COUNT(a) FROM AuditLog a WHERE " + FILTERS)
    Page<AuditLog> search(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                          @Param("actor") String actorEmail, @Param("role") String actorRole,
                          @Param("module") AuditModule module, @Param("action") AuditAction action,
                          @Param("text") String text, Pageable page);

    String FILTERS = "(:from IS NULL OR a.timestamp >= :from) AND (:to IS NULL OR a.timestamp < :to) "
            + "AND (:actor IS NULL OR a.actorEmail = :actor) AND (:role IS NULL OR a.actorRole = :role) "
            + "AND (:module IS NULL OR a.module = :module) AND (:action IS NULL OR a.action = :action) "
            + "AND (:text IS NULL OR LOWER(a.description) LIKE :text OR LOWER(a.actorName) LIKE :text "
            + "OR LOWER(a.actorEmail) LIKE :text OR LOWER(a.entityId) LIKE :text)";

    // Personas que aparecen en el registro (para el filtro "Usuario"). Se saltean los ingresos
    // fallidos: ahí el email es el que se intentó, no el de alguien que entró.
    @Query("SELECT a.actorEmail, MAX(a.actorName) FROM AuditLog a WHERE a.actorEmail IS NOT NULL "
            + "AND a.action <> com.dreambake.entity.AuditAction.LOGIN_FAILED GROUP BY a.actorEmail ORDER BY MAX(a.actorName)")
    List<Object[]> findActors();
}
