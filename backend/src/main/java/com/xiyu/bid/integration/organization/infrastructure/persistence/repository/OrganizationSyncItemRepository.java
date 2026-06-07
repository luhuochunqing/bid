package com.xiyu.bid.integration.organization.infrastructure.persistence.repository;

import com.xiyu.bid.integration.organization.infrastructure.persistence.entity.OrganizationSyncItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrganizationSyncItemRepository extends JpaRepository<OrganizationSyncItemEntity, Long> {
    List<OrganizationSyncItemEntity> findByRunId(Long runId);

    List<OrganizationSyncItemEntity> findByRunIdAndStatus(Long runId, String status);
}
