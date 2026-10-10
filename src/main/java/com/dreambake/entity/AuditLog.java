package com.dreambake.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Registro de auditoría: quién hizo qué, cuándo y desde dónde (paso 3.1).
 *
 * Es un registro "a prueba de manipulación":
 *  - Ningún campo se puede modificar una vez guardado (updatable = false en todos, y la entidad
 *    es @Immutable: Hibernate nunca manda un UPDATE de esta tabla).
 *  - Cada registro guarda el hash (huella digital SHA-256) de su contenido junto con el hash del
 *    registro anterior. Así los registros forman una cadena: si alguien cambia o borra uno desde
 *    la base de datos, la cadena se rompe en ese punto y AuditService.verifyChain() lo detecta.
 *  - El repositorio (AuditLogRepository) no tiene métodos para borrar.
 *
 * Los enums se guardan como texto (VARCHAR), no como ENUM de MySQL: así se pueden agregar valores
 * nuevos sin tocar la base (lección del paso 1.5, ver EmailLogSchemaFix).
 */
@Entity
@Immutable
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
        @Index(name = "idx_audit_module", columnList = "module"),
        @Index(name = "idx_audit_actor", columnList = "actor_email")
})
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(updatable = false)
    private Long id;

    // Cuándo pasó (con milisegundos; ver AuditService: la hora entra en el hash)
    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;

    // Quién lo hizo. Si no había nadie logueado (tienda pública o tareas automáticas) se anota así.
    @Column(name = "actor_email", length = 150, updatable = false)
    private String actorEmail;

    @Column(name = "actor_name", length = 150, updatable = false)
    private String actorName;

    // ADMIN, PASTELERO, ROLE_USER, PUBLICO (visitante de la tienda) o SISTEMA (tarea automática)
    @Column(name = "actor_role", length = 40, updatable = false)
    private String actorRole;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30, updatable = false)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30, updatable = false)
    private AuditModule module;

    // Sobre qué cosa: por ejemplo entityType = "Pedido" y entityId = "45"
    @Column(name = "entity_type", length = 60, updatable = false)
    private String entityType;

    @Column(name = "entity_id", length = 64, updatable = false)
    private String entityId;

    // Frase en español para leer en la pantalla de auditoría: "Confirmó el pedido #45 de María Gómez"
    @Column(nullable = false, length = 500, updatable = false)
    private String description;

    // JSON con los datos de antes y después (sin contraseñas ni tokens: AuditService los saca)
    @Column(columnDefinition = "TEXT", updatable = false)
    private String details;

    @Column(name = "ip_address", length = 64, updatable = false)
    private String ipAddress;

    @Column(name = "user_agent", length = 255, updatable = false)
    private String userAgent;

    // Hash del registro anterior ("GENESIS" para el primero). Es único: dos registros no pueden
    // colgar del mismo anterior, así la cadena nunca se puede bifurcar (ver AuditService).
    @Column(name = "previous_hash", nullable = false, length = 64, unique = true, updatable = false)
    private String previousHash;

    // SHA-256 de este registro (64 caracteres hexadecimales)
    @Column(nullable = false, length = 64, updatable = false)
    private String hash;
}
