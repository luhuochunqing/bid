package com.xiyu.bid.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 操作日志实体
 * 记录系统中的关键操作
 */
@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_user", columnList = "user_id"),
    @Index(name = "idx_audit_action", columnList = "action"),
    @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
    @Index(name = "idx_audit_entity", columnList = "entity_type, entity_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 执行操作的用户ID
     */
    @Column(name = "user_id", nullable = false)
    private String userId;

    /**
     * 用户名（用于快速查询，冗余字段）
     */
    @Column(name = "username", length = 100)
    private String username;

    /**
     * 操作类型：LOGIN, LOGOUT, CREATE, UPDATE, DELETE, EXPORT, etc.
     */
    @Column(name = "action", nullable = false, length = 50)
    private String action;

    /**
     * 实体类型（如：Project, Tender, User等）
     */
    @Column(name = "entity_type", length = 100)
    private String entityType;

    /**
     * 实体ID
     */
    @Column(name = "entity_id", length = 100)
    private String entityId;

    /**
     * 操作描述
     */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * 操作前的数据（JSON格式）
     */
    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    /**
     * 操作后的数据（JSON格式）
     */
    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    /**
     * 请求的IP地址
     */
    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    /**
     * User-Agent
     */
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /**
     * 操作是否成功
     */
    @Column(name = "success", nullable = false)
    private Boolean success;

    /**
     * 错误信息（如果操作失败）
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** CO-324: 关联项目 id（项目动态按此查询；非项目操作为 null） */
    @Column(name = "project_id")
    private Long projectId;

    /**
     * 操作时间戳
     */
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
        if (success == null) {
            success = true;
        }
    }
}
