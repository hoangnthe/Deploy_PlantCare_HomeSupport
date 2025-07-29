package com.plantcare_backend.repository;

import com.plantcare_backend.model.CareLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CareLogRepository extends JpaRepository<CareLog, Long> {
    List<CareLog> findByUserPlant_UserPlantIdOrderByCreatedAtDesc(Long userPlantId);

    List<CareLog> findByUserPlant_UserIdOrderByCreatedAtDesc(Long userId);

    Page<CareLog> findByUserPlant_UserPlantId(Long userPlantId, Pageable pageable);
}
