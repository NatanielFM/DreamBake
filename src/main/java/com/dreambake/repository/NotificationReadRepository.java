package com.dreambake.repository;

import com.dreambake.entity.NotificationRead;
import com.dreambake.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface NotificationReadRepository extends JpaRepository<NotificationRead, Long> {

    // Claves que esta persona ya marcó como leídas (de entre las que se están mostrando)
    @Query("select r.notificationKey from NotificationRead r where r.user = :user and r.notificationKey in :keys")
    List<String> findReadKeys(@Param("user") User user, @Param("keys") Collection<String> keys);

    // Limpieza: las marcas viejas ya no sirven (esos avisos dejaron de mostrarse hace mucho)
    @Transactional
    @Modifying
    @Query("delete from NotificationRead r where r.readAt < :before")
    int deleteOlderThan(@Param("before") LocalDateTime before);

    // Al dar de baja a un usuario se borran sus marcas (si no, la base no deja borrarlo)
    @Transactional
    @Modifying
    @Query("delete from NotificationRead r where r.user = :user")
    int deleteByUser(@Param("user") User user);
}
