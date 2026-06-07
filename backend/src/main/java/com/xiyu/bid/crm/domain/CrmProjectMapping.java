package com.xiyu.bid.crm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * CRM 项目映射实体
 * 存储业主单位到 CRM 项目负责人的映射关系
 */
@Entity
@Table(name = "crm_project_mapping", indexes = {
    @Index(name = "idx_crm_project_manager", columnList = "project_manager_id"),
    @Index(name = "idx_department", columnList = "department_id")
})
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CrmProjectMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 业主单位名称（唯一）
     */
    @Column(name = "purchaser_name", nullable = false, length = 500, unique = true)
    private String purchaserName;

    /**
     * CRM 项目ID
     */
    @Column(name = "crm_project_id", length = 100)
    private String crmProjectId;

    /**
     * CRM 项目负责人ID
     */
    @Column(name = "project_manager_id", length = 100)
    private String projectManagerId;

    /**
     * CRM 项目负责人姓名
     */
    @Column(name = "project_manager_name", length = 100)
    private String projectManagerName;

    /**
     * 部门ID
     */
    @Column(name = "department_id", length = 100)
    private String departmentId;

    /**
     * 部门名称
     */
    @Column(name = "department_name", length = 200)
    private String departmentName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
