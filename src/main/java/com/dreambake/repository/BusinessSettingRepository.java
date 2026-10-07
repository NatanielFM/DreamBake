package com.dreambake.repository;

import com.dreambake.entity.BusinessSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BusinessSettingRepository extends JpaRepository<BusinessSetting, String> {
}
