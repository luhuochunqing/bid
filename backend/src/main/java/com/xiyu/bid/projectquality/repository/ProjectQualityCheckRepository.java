package com.xiyu.bid.projectquality.repository;

import com.xiyu.bid.projectquality.entity.ProjectQualityCheck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectQualityCheckRepository extends JpaRepository<ProjectQualityCheck, Long> {
    Optional<ProjectQualityCheck> findFirstByProjectIdOrderByCheckedAtDesc(Long projectId);
}
