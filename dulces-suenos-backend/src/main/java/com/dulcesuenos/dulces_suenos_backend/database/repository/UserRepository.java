package com.dulcesuenos.dulces_suenos_backend.database.repository;


import org.springframework.data.jpa.repository.JpaRepository;
import com.dulcesuenos.dulces_suenos_backend.database.entity.User;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
}
