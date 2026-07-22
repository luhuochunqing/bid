package com.xiyu.bid.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Table(name = "roles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(name = "is_system", nullable = false)
    @Builder.Default
    private Boolean isSystem = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "data_scope", nullable = false, length = 32)
    @Builder.Default
    private String dataScope = "self";

    @Column(name = "menu_permissions", length = 4000)
    private String menuPermissionsValue;

    @Column(name = "allowed_projects", length = 4000)
    private String allowedProjectsValue;

    @Column(name = "allowed_depts", length = 4000)
    private String allowedDeptsValue;

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

    public List<String> getMenuPermissions() {
        return splitStrings(menuPermissionsValue);
    }

    public void setMenuPermissions(List<String> permissions) {
        this.menuPermissionsValue = joinStrings(permissions);
    }

    public List<Long> getAllowedProjects() {
        return splitStrings(allowedProjectsValue).stream()
                .map(value -> {
                    try {
                        return Long.parseLong(value);
                    } catch (NumberFormatException ignored) {
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public void setAllowedProjects(List<Long> projectIds) {
        this.allowedProjectsValue = projectIds == null ? null : projectIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    public List<String> getAllowedDepts() {
        return splitStrings(allowedDeptsValue);
    }

    public void setAllowedDepts(List<String> deptCodes) {
        this.allowedDeptsValue = joinStrings(deptCodes);
    }

    private static List<String> splitStrings(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return List.of();
        }
        return Arrays.stream(rawValue.split(","))
                .map(String::trim)
                // 防御性去引号：历史 Flyway 迁移 V1118/V1121/V1122/V1123 错误地用
                // CONCAT(menu_permissions, ',"tender.view"') 把字面双引号写入了字段值，
                // 导致权限字符串变为 "tender.view"（带引号），无法匹配 hasAuthority('tender.view')。
                // 这里在解析时去掉首尾字面双引号，兼容历史脏数据。
                .map(RoleProfile::stripSurroundingQuotes)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private static String stripSurroundingQuotes(String value) {
        if (value == null || value.length() < 2) {
            return value;
        }
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String joinStrings(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.stream()
                .map(value -> value == null ? "" : value.trim())
                .filter(value -> !value.isBlank())
                .distinct()
                .collect(Collectors.joining(","));
    }
}
