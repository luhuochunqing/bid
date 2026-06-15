package com.xiyu.bid.integration.organization.infrastructure.persistence.repository;

import com.xiyu.bid.integration.organization.infrastructure.persistence.entity.OrganizationDepartmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrganizationDepartmentRepository extends JpaRepository<OrganizationDepartmentEntity, String> {
    Optional<OrganizationDepartmentEntity> findBySourceAppAndExternalDeptId(String sourceApp, String externalDeptId);

    Optional<OrganizationDepartmentEntity> findBySourceAppAndDepartmentCode(String sourceApp, String departmentCode);

    List<OrganizationDepartmentEntity> findByEnabledTrueOrderByDepartmentCode();
}
