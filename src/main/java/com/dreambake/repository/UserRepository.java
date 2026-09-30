package com.dreambake.repository;

import com.dreambake.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Método personalizado para buscar por email (útil para el Login)
    Optional<User> findByEmail(String email);

    // Buscar usuarios por rol
    java.util.List<User> findByRoles(String roles);
}
