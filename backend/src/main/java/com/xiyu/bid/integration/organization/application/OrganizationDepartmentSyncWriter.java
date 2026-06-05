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
        OrganizationDepartmentEntity department = departmentRepository
                .findBySourceAppAndExternalDeptId(sourceApp, plan.externalDeptId())
                .orElseGet(OrganizationDepartmentEntity::new);
        department.setDepartmentCode(plan.departmentCode());
        department.setExternalDeptId(plan.externalDeptId());
        department.setDepartmentName(plan.departmentName());
        department.setParentExternalDeptId(plan.parentExternalDeptId());
        department.setParentDepartmentCode(plan.parentDepartmentCode());
        department.setSourceApp(sourceApp);
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
