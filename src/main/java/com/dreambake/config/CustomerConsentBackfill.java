package com.dreambake.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Completa los datos de consentimiento de los clientes que ya existían antes del paso 1.2.
 *
 * Cuando Hibernate agrega las columnas nuevas, las filas viejas quedan con NULL. Al arrancar:
 *  - marketing_consent NULL → 0: los clientes existentes quedan SIN consentimiento (nunca lo dieron).
 *  - unsubscribe_token NULL → un código al azar (UUID() de MySQL), para su futuro link de baja.
 * Si no hay nada que completar, no cambia nada.
 */
@Component
public class CustomerConsentBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CustomerConsentBackfill.class);

    private final JdbcTemplate jdbc;

    public CustomerConsentBackfill(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        int consent = jdbc.update("UPDATE customers SET marketing_consent = 0 WHERE marketing_consent IS NULL");
        int tokens = jdbc.update("UPDATE customers SET unsubscribe_token = UUID() WHERE unsubscribe_token IS NULL");
        if (consent > 0 || tokens > 0) {
            log.info("Clientes existentes: {} quedaron sin consentimiento de emails y {} recibieron su código de baja.",
                    consent, tokens);
        }
    }
}
