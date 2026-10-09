package com.dreambake.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Convierte la columna email_logs.type de ENUM de MySQL a texto (VARCHAR).
 *
 * Por qué hace falta: cuando Hibernate crea la tabla, guarda los tipos de email como un ENUM
 * de MySQL con los valores que existían ese día. Si después se agrega un tipo nuevo al código
 * (por ejemplo DELIVERY_SUMMARY), "ddl-auto=update" NO modifica el ENUM y MySQL rechaza el dato
 * ("Data truncated for column 'type'"). Con VARCHAR, los tipos nuevos funcionan sin tocar la base.
 *
 * Es seguro correrlo todas las veces: si la columna ya es texto (o la tabla es nueva), no hace nada.
 * Corre antes que las tareas de arranque (@Order) para que ya esté lista cuando se manden emails.
 */
@Component
@Order(0)
public class EmailLogSchemaFix implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EmailLogSchemaFix.class);

    private final JdbcTemplate jdbc;

    public EmailLogSchemaFix(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            String dataType = jdbc.query(
                    "SELECT DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                            + "AND TABLE_NAME = 'email_logs' AND COLUMN_NAME = 'type'",
                    rs -> rs.next() ? rs.getString(1) : null);
            if ("enum".equalsIgnoreCase(dataType)) {
                jdbc.execute("ALTER TABLE email_logs MODIFY COLUMN type VARCHAR(40)");
                log.info("Historial de emails: la columna 'type' pasó de ENUM a texto (para admitir tipos nuevos).");
            }
        } catch (Exception e) {
            log.error("No se pudo revisar la columna email_logs.type: {}", e.getMessage());
        }
    }
}
