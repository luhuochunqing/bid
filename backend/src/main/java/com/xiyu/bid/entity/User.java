package com.xiyu.bid.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(length = 32)
    private String phone;

    @Column(nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = true)
    private RoleProfile roleProfile;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private Boolean emailVerified = false;

    @Column(name = "department_code", length = 100)
    private String departmentCode;

    @Column(name = "department_name", length = 100)
    private String departmentName;

    @Column(name = "wecom_user_id", length = 64)
    private String wecomUserId;

    @Column(name = "external_org_user_id", length = 128)
    private String externalOrgUserId;

    @Column(name = "external_org_source_app", length = 100)
    private String externalOrgSourceApp;

    @Column(name = "last_org_event_key", length = 128)
    private String lastOrgEventKey;

    @Column(name = "last_org_synced_at")
    private LocalDateTime lastOrgSyncedAt;

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

    public enum Role {
        ADMIN,
        MANAGER,
        STAFF;

        public static Role fromCode(String code) {
            if (code == null || code.isBlank()) {
                return STAFF;
            }
            return switch (code.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "admin" -> ADMIN;
                case "manager" -> MANAGER;
                default -> STAFF;
            };
        }
    }

    public String getRoleCode() {
        if (roleProfile != null && roleProfile.getCode() != null && !roleProfile.getCode().isBlank()) {
            return roleProfile.getCode().trim().toLowerCase(java.util.Locale.ROOT);
        }
        return role == null ? "staff" : role.name().toLowerCase(java.util.Locale.ROOT);
    }

    public String getRoleName() {
        if (roleProfile != null && roleProfile.getName() != null && !roleProfile.getName().isBlank()) {
            return roleProfile.getName();
        }
        return switch (getRole()) {
            case ADMIN -> "管理员";
            case MANAGER -> "经理";
            case STAFF -> "员工";
        };
    }
}
