package com.xiyu.bid.workflowform.infrastructure.persistence.repository;

import com.xiyu.bid.workflowform.infrastructure.persistence.entity.OaProcessBindingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OaProcessBindingJpaRepository extends JpaRepository<OaProcessBindingEntity, String> {
}
