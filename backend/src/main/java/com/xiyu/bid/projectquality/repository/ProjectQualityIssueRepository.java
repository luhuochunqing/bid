package com.xiyu.bid.projectquality.repository;

import com.xiyu.bid.projectquality.entity.ProjectQualityIssue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectQualityIssueRepository extends JpaRepository<ProjectQualityIssue, Long> {
    List<ProjectQualityIssue> findByCheckIdOrderByIdAsc(Long checkId);
}
