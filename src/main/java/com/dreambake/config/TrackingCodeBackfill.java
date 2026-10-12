package com.dreambake.config;

import com.dreambake.entity.TrackingCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Les da código de seguimiento a los pedidos que ya existían antes del paso 6.5.
 *
 * Al agregar la columna, Hibernate deja NULL en las filas viejas. Al arrancar, cada pedido sin código
 * recibe uno al azar. Se usa SQL directo porque la columna es updatable = false para JPA (así nadie
 * la cambia por error desde el panel). Si el código sale repetido (casi imposible), se prueba otro.
 */
@Component
public class TrackingCodeBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TrackingCodeBackfill.class);

    private final JdbcTemplate jdbc;

    public TrackingCodeBackfill(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Long> ids = jdbc.queryForList("SELECT id FROM orders_table WHERE tracking_code IS NULL", Long.class);
        int done = 0;
        for (Long id : ids) {
            for (int attempt = 0; attempt < 5; attempt++) {
                try {
                    done += jdbc.update("UPDATE orders_table SET tracking_code = ? WHERE id = ? AND tracking_code IS NULL",
                            TrackingCodes.generate(), id);
                    break;
                } catch (DuplicateKeyException e) {
                    // el código ya lo tenía otro pedido: se genera otro
                }
            }
        }
        if (done > 0) log.info("Seguimiento de pedidos: {} pedidos existentes recibieron su código.", done);
    }
}
