package com.xiyu.bid.integration.infrastructure.persistence.repository;

import com.xiyu.bid.integration.infrastructure.persistence.entity.WeComIntegrationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for WeComIntegrationEntity.
 * Single-row table: always use findById(1L) / save().
 */
public interface WeComIntegrationJpaRepository extends JpaRepository<WeComIntegrationEntity, Long> {
}
