package com.xiyu.bid.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "project_groups", indexes = {
        @Index(name = "idx_project_group_code", columnList = "group_code", unique = true),
        @Index(name = "idx_project_group_manager", columnList = "manager_user_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_code", nullable = false, unique = true, length = 100)
    private String groupCode;

    @Column(name = "group_name", nullable = false, length = 200)
    private String groupName;

    @Column(name = "manager_user_id")
    private Long managerUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 30)
    private Visibility visibility;

    @ElementCollection
    @CollectionTable(name = "project_group_members", joinColumns = @JoinColumn(name = "project_group_id"))
    @Column(name = "user_id")
    @Builder.Default
    private List<Long> memberUserIds = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "project_group_role_access", joinColumns = @JoinColumn(name = "project_group_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role_code", length = 50)
    @Builder.Default
    private List<AccessRole> allowedRoles = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "project_group_projects", joinColumns = @JoinColumn(name = "project_group_id"))
    @Column(name = "project_id")
    @Builder.Default
    private List<Long> projectIds = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
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

    public enum Visibility {
        ALL,
        MEMBERS,
        MANAGER,
        CUSTOM
    }

    public enum AccessRole {
        ADMIN,
        MANAGER,
        STAFF
    }
}
