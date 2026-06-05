package com.xiyu.bid.integration.organization.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "organization_departments")
public class OrganizationDepartmentEntity {
    @Id
    @Column(name = "department_code", length = 100)
    private String departmentCode;

    @Column(name = "external_dept_id", length = 128)
    private String externalDeptId;

    @Column(name = "department_name", nullable = false, length = 100)
    private String departmentName;

    @Column(name = "parent_department_code", length = 100)
    private String parentDepartmentCode;

    @Column(name = "parent_external_dept_id", length = 128)
    private String parentExternalDeptId;

    @Column(name = "source_app", length = 100)
    private String sourceApp;

    @Column(name = "last_event_key", length = 128)
    private String lastEventKey;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }
}
