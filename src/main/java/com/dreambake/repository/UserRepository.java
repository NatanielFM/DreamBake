package com.dreambake.repository;

import com.dreambake.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Método personalizado para buscar por email (útil para el Login)
    Optional<User> findByEmail(String email);

    // Usuario vinculado a una cuenta de Google o Facebook (por el id que da la red social)
    Optional<User> findByAuthProviderAndProviderId(com.dreambake.entity.AuthProvider authProvider, String providerId);

    // Buscar usuarios por rol
    java.util.List<User> findByRoles(String roles);

    // Guarda solo la fecha del último ingreso (no pisa ningún otro campo del usuario)
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query("update User u set u.lastLoginAt = :at where u.email = :email")
    int updateLastLogin(@org.springframework.data.repository.query.Param("email") String email,
                        @org.springframework.data.repository.query.Param("at") java.time.LocalDateTime at);
}
