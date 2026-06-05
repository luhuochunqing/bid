package com.xiyu.bid.ai.repository;

import com.xiyu.bid.ai.entity.ProjectScorePreview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectScorePreviewRepository extends JpaRepository<ProjectScorePreview, Long> {

    Optional<ProjectScorePreview> findFirstByProjectIdOrderByCreatedAtDesc(Long projectId);
}
