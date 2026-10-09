package com.dreambake.repository;

import com.dreambake.entity.Customer;
import com.dreambake.entity.EmailLog;
import com.dreambake.entity.EmailStatus;
import com.dreambake.entity.EmailType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {
    // Historial de un cliente, del más nuevo al más viejo (ficha del cliente)
    List<EmailLog> findByCustomerOrderBySentAtDesc(Customer customer);

    // ¿Ya se le mandó un email de este tipo, con ese resultado, en el período dado? (evita saludos repetidos)
    boolean existsByCustomerAndTypeAndStatusAndSentAtBetween(Customer customer, EmailType type, EmailStatus status,
                                                             LocalDateTime from, LocalDateTime to);
}
