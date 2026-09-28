package com.dreambake.repository;

import com.dreambake.entity.CakeOption;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CakeOptionRepository extends JpaRepository<CakeOption, Long> {
    List<CakeOption> findByEnabledTrueOrderByIdAsc();

    List<CakeOption> findAllByOrderByCategoryAscIdAsc();
}