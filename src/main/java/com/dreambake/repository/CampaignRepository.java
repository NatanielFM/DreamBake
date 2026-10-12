package com.dreambake.repository;

import com.dreambake.entity.Campaign;
import com.dreambake.entity.CampaignStatus;
import com.dreambake.entity.Coupon;
import com.dreambake.entity.CustomerTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {

    // Lista del panel: la más nueva primero
    List<Campaign> findAllByOrderByCreatedAtDesc();

    // Programadas cuya hora ya llegó (las dispara CampaignScheduler)
    List<Campaign> findByStatusAndScheduledAtLessThanEqual(CampaignStatus status, LocalDateTime now);

    List<Campaign> findByStatus(CampaignStatus status);

    // ¿Algún cupón / etiqueta lo usa una campaña que todavía no terminó? (no se deja borrar)
    boolean existsByCouponAndStatusIn(Coupon coupon, Collection<CampaignStatus> statuses);
    boolean existsByTagAndStatusIn(CustomerTag tag, Collection<CampaignStatus> statuses);

    // Campañas (terminadas) que usan el cupón / la etiqueta: se les quita la referencia antes de borrarlos
    List<Campaign> findByCoupon(Coupon coupon);
    List<Campaign> findByTag(CustomerTag tag);

    // ===== Cambios puntuales que hace el envío en segundo plano =====
    // Son UPDATE de una sola columna (y no un save() de toda la campaña) para no pisar un cambio
    // hecho al mismo tiempo desde el panel, por ejemplo una cancelación.

    @Modifying
    @Transactional
    @Query("UPDATE Campaign c SET c.sentCount = :sent, c.failedCount = :failed WHERE c.id = :id")
    int updateCounts(@Param("id") Long id, @Param("sent") int sent, @Param("failed") int failed);

    // Pausa (tope diario o errores seguidos): solo si sigue enviándose
    @Modifying
    @Transactional
    @Query("UPDATE Campaign c SET c.statusNote = :note, c.nextAttemptAt = :next WHERE c.id = :id "
            + "AND c.status = com.dreambake.entity.CampaignStatus.SENDING")
    int pause(@Param("id") Long id, @Param("note") String note, @Param("next") LocalDateTime next);

    // Termina: pasa a SENT solo si sigue enviándose (si la cancelaron, queda cancelada)
    @Modifying
    @Transactional
    @Query("UPDATE Campaign c SET c.status = com.dreambake.entity.CampaignStatus.SENT, c.statusNote = null, "
            + "c.nextAttemptAt = null, c.totalRecipients = :total WHERE c.id = :id "
            + "AND c.status = com.dreambake.entity.CampaignStatus.SENDING")
    int finish(@Param("id") Long id, @Param("total") int total);

    // Cancelar (desde el panel): solo si está programada o enviándose.
    // clearAutomatically: después del UPDATE se olvida la copia en memoria, así el findById
    // siguiente lee de la base el estado nuevo (y no la campaña vieja).
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE Campaign c SET c.status = com.dreambake.entity.CampaignStatus.CANCELLED, c.statusNote = null, "
            + "c.nextAttemptAt = null WHERE c.id = :id AND c.status IN (com.dreambake.entity.CampaignStatus.SCHEDULED, "
            + "com.dreambake.entity.CampaignStatus.SENDING)")
    int cancel(@Param("id") Long id);

    // Se retoma: se borra el aviso de pausa
    @Modifying
    @Transactional
    @Query("UPDATE Campaign c SET c.statusNote = null, c.nextAttemptAt = null WHERE c.id = :id")
    int clearPause(@Param("id") Long id);
}
