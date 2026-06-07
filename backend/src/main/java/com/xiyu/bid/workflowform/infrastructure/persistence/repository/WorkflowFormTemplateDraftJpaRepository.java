package com.xiyu.bid.workflowform.infrastructure.persistence.repository;

import com.xiyu.bid.workflowform.infrastructure.persistence.entity.WorkflowFormTemplateDraftEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WorkflowFormTemplateDraftJpaRepository extends JpaRepository<WorkflowFormTemplateDraftEntity, Long> {
    Optional<WorkflowFormTemplateDraftEntity> findByTemplateCode(String templateCode);
}
