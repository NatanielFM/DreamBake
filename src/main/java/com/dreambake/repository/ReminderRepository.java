package com.dreambake.repository;

import com.dreambake.entity.Reminder;
import com.dreambake.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Recordatorios (paso 5.2). Los métodos "Owner" son de una persona; los demás, de todo el equipo (admin). */
public interface ReminderRepository extends JpaRepository<Reminder, Long> {

    // Pendientes que vencen antes de "hasta" (hoy + vencidos: hasta = mañana a las 00:00)
    @Query("select r from Reminder r where r.done = false and r.dueAt < :until and (:owner is null or r.owner = :owner) order by r.dueAt")
    List<Reminder> findPendingUntil(@Param("owner") User owner, @Param("until") LocalDateTime until);

    // Los que se marcaron como hechos desde "desde" (los de hoy, para que no desaparezcan de golpe)
    @Query("select r from Reminder r where r.done = true and r.doneAt >= :since and (:owner is null or r.owner = :owner) order by r.dueAt")
    List<Reminder> findDoneSince(@Param("owner") User owner, @Param("since") LocalDateTime since);

    // Todos los pendientes, sin importar la fecha
    @Query("select r from Reminder r where r.done = false and (:owner is null or r.owner = :owner) order by r.dueAt")
    List<Reminder> findPending(@Param("owner") User owner);

    // Todos (los más nuevos primero), con tope
    @Query("select r from Reminder r where (:owner is null or r.owner = :owner) order by r.dueAt desc")
    List<Reminder> findLatest(@Param("owner") User owner, Pageable page);

    // Para el aviso del panel: los de esta persona que ya llegaron a su hora y no están hechos
    List<Reminder> findByOwnerAndDoneFalseAndDueAtLessThanEqualOrderByDueAt(User owner, LocalDateTime now);

    // Para el email: pendientes sin email mandado cuya hora está entre "desde" y "hasta" (ahora y +1 h)
    @Query("select r from Reminder r where r.done = false and r.emailSentAt is null and r.dueAt > :from and r.dueAt <= :to order by r.dueAt")
    List<Reminder> findPendingForEmail(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /*
     * Cambios puntuales de UN campo (UPDATE … SET campo = …) en vez de guardar el recordatorio entero:
     * la tarea del email y la persona que toca "Hecho" pueden pasar al mismo tiempo, y guardar el objeto
     * completo podría pisar el cambio del otro (por ejemplo, volver a dejarlo pendiente).
     */
    @Transactional
    @Modifying
    @Query("update Reminder r set r.done = :done, r.doneAt = :doneAt where r.id = :id")
    int updateDone(@Param("id") Long id, @Param("done") boolean done, @Param("doneAt") LocalDateTime doneAt);

    @Transactional
    @Modifying
    @Query("update Reminder r set r.emailSentAt = :at where r.id = :id and r.emailSentAt is null")
    int markEmailSent(@Param("id") Long id, @Param("at") LocalDateTime at);

    // Al dar de baja a un usuario se borran sus recordatorios (si no, la base no deja borrarlo)
    @Transactional
    @Modifying
    @Query("delete from Reminder r where r.owner = :owner")
    int deleteByOwner(@Param("owner") User owner);
}
