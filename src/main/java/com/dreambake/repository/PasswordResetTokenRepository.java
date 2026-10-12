package com.dreambake.repository;

import com.dreambake.entity.PasswordResetToken;
import com.dreambake.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    // Se busca por la huella del código (nunca por el código en sí, que no se guarda)
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    // Al cambiar la contraseña se anulan TODOS los links de esa persona que sigan sirviendo
    // (si pidió dos y usó uno, el otro ya no tiene que andar)
    @Modifying
    @Query("update PasswordResetToken t set t.used = true, t.usedAt = :at where t.user = :user and t.used = false")
    int invalidateAll(@Param("user") User user, @Param("at") LocalDateTime at);

    // Al dar de baja a un usuario se borran sus links (si no, la base no deja borrarlo)
    @Transactional
    @Modifying
    @Query("delete from PasswordResetToken t where t.user = :user")
    int deleteByUser(@Param("user") User user);

    // Limpieza: links vencidos hace más de un día (ya no sirven para nada)
    @Transactional
    @Modifying
    @Query("delete from PasswordResetToken t where t.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") LocalDateTime before);
}
