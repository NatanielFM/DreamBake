package com.dreambake.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Protección del registro de auditoría DESDE LA BASE DE DATOS (paso 3.2).
 *
 * La aplicación ya no tiene forma de borrar ni modificar la auditoría (AuditLogRepository no tiene
 * esos métodos y AuditLog es @Immutable). Pero alguien podría entrar directo a MySQL (con Workbench o
 * la consola) y hacer un UPDATE o un DELETE. Para eso se crean dos TRIGGERS: reglas que MySQL ejecuta
 * solo, ANTES de cada UPDATE o DELETE sobre audit_logs, y que cancelan la operación con un error
 * (SIGNAL SQLSTATE '45000', el código de "error definido por el usuario").
 *
 * Se crean al arrancar la app, solo si no existen. ¿Por qué no "CREATE TRIGGER IF NOT EXISTS"?
 * Existe desde MySQL 8.0.29 (esta PC tiene 8.0.46), pero preguntar primero en
 * information_schema.TRIGGERS funciona en cualquier versión de MySQL 8 y deja claro en el log si
 * se crearon o ya estaban.
 *
 * Si la cuenta de MySQL de la app no tiene permiso para crear triggers (pasa con un usuario
 * restringido, paso 7.2, o si el registro binario de MySQL exige el privilegio SUPER), NO se corta
 * el arranque: se avisa en el log para que el administrador los cree una vez con root.
 */
@Component
@Order(0)
public class AuditDatabaseInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AuditDatabaseInitializer.class);

    static final String MESSAGE = "El registro de auditoría no se puede modificar ni borrar";
    static final String UPDATE_TRIGGER = "audit_logs_no_update";
    static final String DELETE_TRIGGER = "audit_logs_no_delete";

    private final JdbcTemplate jdbc;

    public AuditDatabaseInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        createIfMissing(UPDATE_TRIGGER, "UPDATE");
        createIfMissing(DELETE_TRIGGER, "DELETE");
    }

    private void createIfMissing(String name, String event) {
        try {
            Integer existing = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.TRIGGERS " +
                    "WHERE TRIGGER_SCHEMA = DATABASE() AND TRIGGER_NAME = ?", Integer.class, name);
            if (existing != null && existing > 0) {
                log.info("Auditoría: el trigger {} ya existe.", name);
                return;
            }
            // Un trigger de una sola instrucción: no hace falta BEGIN … END ni cambiar el delimitador
            jdbc.execute("CREATE TRIGGER " + name + " BEFORE " + event + " ON audit_logs FOR EACH ROW " +
                    "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '" + MESSAGE + "'");
            log.info("Auditoría: se creó el trigger {} (bloquea {} en audit_logs).", name, event);
        } catch (Exception e) {
            log.warn("Auditoría: NO se pudo crear el trigger {} ({}). La auditoría sigue funcionando, pero "
                    + "MySQL no impide modificarla: crealo una vez con el usuario root.", name, e.getMessage());
        }
    }
}
