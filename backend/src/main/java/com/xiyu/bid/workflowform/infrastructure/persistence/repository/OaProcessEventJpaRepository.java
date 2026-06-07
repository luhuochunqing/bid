package com.xiyu.bid.workflowform.infrastructure.persistence.repository;

import com.xiyu.bid.workflowform.infrastructure.persistence.entity.OaProcessEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OaProcessEventJpaRepository extends JpaRepository<OaProcessEventEntity, Long> {
    boolean existsByEventId(String eventId);
}
