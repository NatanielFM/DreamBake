package com.dreambake.repository;

import com.dreambake.entity.CustomerTag;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CustomerTagRepository extends JpaRepository<CustomerTag, Long> {
    Optional<CustomerTag> findByName(String name);
}
