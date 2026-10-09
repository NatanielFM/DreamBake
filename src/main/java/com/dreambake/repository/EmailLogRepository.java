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

    // ===== Campañas =====
    // ¿Este cliente ya tiene un intento (enviado o fallido) de esta campaña? Así se retoma sin repetir.
    boolean existsByCampaignIdAndCustomer(Long campaignId, Customer customer);

    // Métricas de una campaña: cuántos se enviaron y cuántos fallaron
    long countByCampaignIdAndStatus(Long campaignId, EmailStatus status);

    // ===== Avisos de pedidos =====
    // ¿Ya se mandó este aviso (tipo) para este pedido? Así no se repite el mismo aviso para el mismo estado.
    boolean existsByOrderIdAndTypeAndStatus(Long orderId, EmailType type, EmailStatus status);

    // ¿Ya se mandó un email de este tipo en el período? (el resumen diario al negocio sale una vez por día)
    boolean existsByTypeAndStatusAndSentAtBetween(EmailType type, EmailStatus status, LocalDateTime from, LocalDateTime to);

    // Emails enviados en un período, de cualquier tipo (para el tope diario de Gmail)
    long countByStatusAndSentAtBetween(EmailStatus status, LocalDateTime from, LocalDateTime to);
}
