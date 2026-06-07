package com.xiyu.bid.integration.organization.infrastructure.persistence.repository;

import com.xiyu.bid.integration.organization.infrastructure.persistence.entity.OrganizationDepartmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrganizationDepartmentRepository extends JpaRepository<OrganizationDepartmentEntity, String> {
    Optional<OrganizationDepartmentEntity> findBySourceAppAndExternalDeptId(String sourceApp, String externalDeptId);
}
