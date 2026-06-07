package com.xiyu.bid.projectworkflow.repository;

import com.xiyu.bid.projectworkflow.entity.ProjectShareLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectShareLinkRepository extends JpaRepository<ProjectShareLink, Long> {

    List<ProjectShareLink> findByProjectIdOrderByCreatedAtDesc(Long projectId);
}
