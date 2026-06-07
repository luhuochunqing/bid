package com.xiyu.bid.workflowform.infrastructure.persistence.repository;

import com.xiyu.bid.workflowform.infrastructure.persistence.entity.WorkflowFormTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowFormTemplateJpaRepository extends JpaRepository<WorkflowFormTemplateEntity, String> {
}
