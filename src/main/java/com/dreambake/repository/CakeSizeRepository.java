package com.dreambake.repository;

import com.dreambake.entity.CakeSize;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CakeSizeRepository extends JpaRepository<CakeSize, Long> {
    List<CakeSize> findByEnabledTrueOrderByIdAsc();

    List<CakeSize> findAllByOrderByIdAsc();
}