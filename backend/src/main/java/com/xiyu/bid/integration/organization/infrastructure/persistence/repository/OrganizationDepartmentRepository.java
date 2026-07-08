package com.xiyu.bid.integration.organization.infrastructure.persistence.repository;

import com.xiyu.bid.integration.organization.infrastructure.persistence.entity.OrganizationDepartmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrganizationDepartmentRepository extends JpaRepository<OrganizationDepartmentEntity, String> {
    Optional<OrganizationDepartmentEntity> findBySourceAppAndExternalDeptId(String sourceApp, String externalDeptId);

    Optional<OrganizationDepartmentEntity> findBySourceAppAndDepartmentCode(String sourceApp, String departmentCode);

    List<OrganizationDepartmentEntity> findByEnabledTrueOrderByDepartmentCode();

    List<OrganizationDepartmentEntity> findBySourceAppAndEnabledTrueOrderByDepartmentCode(String sourceApp);

    /**
     * 批量根据 external_dept_id 查询部门（用于列表场景下通过 users.department_code 反查部门名）。
     * 注意：生产环境 users.department_code 实际存的是 OSS 的 external_dept_id（见 OrganizationDirectoryJsonMapper）。
     */
    List<OrganizationDepartmentEntity> findBySourceAppAndExternalDeptIdIn(String sourceApp, Collection<String> externalDeptIds);
}
