package com.xiyu.bid.integration.organization.infrastructure.persistence.repository;

import com.xiyu.bid.integration.organization.infrastructure.persistence.entity.OrganizationSyncRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrganizationSyncRunRepository extends JpaRepository<OrganizationSyncRunEntity, Long> {
    Optional<OrganizationSyncRunEntity> findByRunKey(String runKey);

    Optional<OrganizationSyncRunEntity> findTopByOrderByStartedAtDesc();

    boolean existsByRunKey(String runKey);
}
