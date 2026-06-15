package com.xiyu.bid.integration.organization.application;

import com.xiyu.bid.integration.organization.domain.OrganizationDepartmentSnapshot;
import com.xiyu.bid.integration.organization.domain.OrganizationDepartmentSyncPlan;
import com.xiyu.bid.integration.organization.domain.OrganizationSyncPolicy;
import com.xiyu.bid.integration.organization.infrastructure.persistence.entity.OrganizationDepartmentEntity;
import com.xiyu.bid.integration.organization.infrastructure.persistence.repository.OrganizationDepartmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OrganizationDepartmentSyncWriter {
    private final OrganizationDepartmentRepository departmentRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OrganizationDepartmentEntity upsert(String sourceApp, String eventKey, OrganizationDepartmentSnapshot snapshot) {
        OrganizationDepartmentSyncPlan plan = OrganizationSyncPolicy.planDepartmentSync(snapshot);

        // API 返回 parentId（externalDeptId）但不返回 parentCode，
        // 导致 parentDepartmentCode 始终为空。从数据库反查已同步的父级部门来补全。
        String resolvedParentDeptCode = plan.parentDepartmentCode();
        if ((resolvedParentDeptCode == null || resolvedParentDeptCode.isBlank())
                && plan.parentExternalDeptId() != null && !plan.parentExternalDeptId().isBlank()) {
            resolvedParentDeptCode = departmentRepository
                    .findBySourceAppAndExternalDeptId(sourceApp, plan.parentExternalDeptId())
                    .map(OrganizationDepartmentEntity::getDepartmentCode)
                    .orElse("");
        }

        // 按业务主键 (source_app, department_code) 查找，避免不同 source_app 互相覆盖。
        OrganizationDepartmentEntity department = departmentRepository
                .findBySourceAppAndDepartmentCode(sourceApp, plan.departmentCode())
                .orElseGet(OrganizationDepartmentEntity::new);
        department.setSourceApp(sourceApp);
        department.setDepartmentCode(plan.departmentCode());
        department.setExternalDeptId(plan.externalDeptId());
        department.setDepartmentName(plan.departmentName());
        department.setParentExternalDeptId(plan.parentExternalDeptId());
        department.setParentDepartmentCode(resolvedParentDeptCode);
        department.setLastEventKey(eventKey);
        department.setLastSyncedAt(LocalDateTime.now());
        department.setEnabled(plan.enabled());
        return departmentRepository.save(department);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void disableByExternalId(String sourceApp, String eventKey, String externalDeptId) {
        departmentRepository.findBySourceAppAndExternalDeptId(sourceApp, externalDeptId)
                .ifPresent(department -> {
                    department.setEnabled(false);
                    department.setLastEventKey(eventKey);
                    department.setLastSyncedAt(LocalDateTime.now());
                    departmentRepository.save(department);
                });
    }
}
