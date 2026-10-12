package com.dreambake.repository;

import com.dreambake.entity.BusinessProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessProfileRepository extends JpaRepository<BusinessProfile, Long> {
}
