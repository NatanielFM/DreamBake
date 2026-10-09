package com.dreambake.repository;

import com.dreambake.entity.Customer;
import com.dreambake.entity.EmailLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {
    // Historial de un cliente, del más nuevo al más viejo (ficha del cliente)
    List<EmailLog> findByCustomerOrderBySentAtDesc(Customer customer);
}
