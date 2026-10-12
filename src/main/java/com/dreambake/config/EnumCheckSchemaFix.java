package com.dreambake.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Borra las restricciones CHECK que Hibernate le pone a las columnas de los enums.
 *
 * Por qué hace falta: al crear una tabla, Hibernate 7 agrega a cada columna de un enum guardado como
 * texto (por ejemplo audit_logs.action o backup_records.type) una regla de MySQL del estilo
 *     CHECK (`action` in ('CREATE','UPDATE',...))
 * con los valores que existían ESE día. Si después se agrega un valor nuevo al enum en el código
 * (por ejemplo una acción nueva de Bakey), "ddl-auto=update" NO actualiza esa regla y MySQL rechaza
 * el dato ("Check constraint ... is violated"). Es el mismo problema que tenía el ENUM de email_logs
 * (ver EmailLogSchemaFix), con otra forma.
 *
 * La lista de valores válidos ya la controla Java (el enum), así que la regla en la base sobra.
 * Solo se borran las reglas con esa forma exacta (`columna` in (...)); cualquier otra se respeta.
 *
 * Es seguro correrlo todas las veces: si no hay reglas de ese tipo, no hace nada. Como corre en cada
 * arranque, también limpia las tablas nuevas que Hibernate cree en el futuro.
 * Si el usuario de la base no tiene permiso ALTER (usuario restringido del paso 7.2), solo avisa en el log.
 */
@Component
@Order(0)
public class EnumCheckSchemaFix implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EnumCheckSchemaFix.class);

    // Forma de las reglas que genera Hibernate para un enum: (`columna` in (_utf8mb4'A',_utf8mb4'B',...))
    private static final Pattern ENUM_CHECK = Pattern.compile("^\\(`\\w+` in \\(.*\\)\\)$", Pattern.DOTALL);

    // Nombres de tablas y reglas: solo letras, números y _ (se pegan dentro del ALTER TABLE)
    private static final Pattern SAFE_NAME = Pattern.compile("\\w+");

    private final JdbcTemplate jdbc;

    public EnumCheckSchemaFix(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Una regla CHECK encontrada en la base: tabla, nombre de la regla y su condición
    private record CheckRule(String table, String name, String clause) {}

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<CheckRule> rules = jdbc.query(
                    "SELECT tc.TABLE_NAME, cc.CONSTRAINT_NAME, cc.CHECK_CLAUSE "
                            + "FROM information_schema.CHECK_CONSTRAINTS cc "
                            + "JOIN information_schema.TABLE_CONSTRAINTS tc "
                            + "  ON tc.CONSTRAINT_SCHEMA = cc.CONSTRAINT_SCHEMA AND tc.CONSTRAINT_NAME = cc.CONSTRAINT_NAME "
                            + "WHERE cc.CONSTRAINT_SCHEMA = DATABASE() AND tc.CONSTRAINT_TYPE = 'CHECK'",
                    (rs, i) -> new CheckRule(rs.getString(1), rs.getString(2), rs.getString(3)));

            for (CheckRule rule : rules) {
                if (!ENUM_CHECK.matcher(rule.clause().trim()).matches()) continue;   // no es de un enum: se deja
                if (!SAFE_NAME.matcher(rule.table()).matches() || !SAFE_NAME.matcher(rule.name()).matches()) continue;
                try {
                    jdbc.execute("ALTER TABLE `" + rule.table() + "` DROP CHECK `" + rule.name() + "`");
                    log.info("Base: se quitó la regla {} de la tabla {} (lista fija de valores de un enum).",
                            rule.name(), rule.table());
                } catch (Exception e) {
                    log.warn("No se pudo quitar la regla {} de la tabla {}: {}", rule.name(), rule.table(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("No se pudieron revisar las reglas CHECK de la base: {}", e.getMessage());
        }
    }
}
