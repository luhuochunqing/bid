package com.xiyu.bid.task.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 任务扩展字段 schema 实体，映射 V103 迁移的 {@code task_extended_field} 表。
 *
 * <p>该实体描述任务扩展字段的元定义（label、类型、是否必填、下拉选项等），
 * 全平台共享 schema（不做 per-project / per-template）。实际扩展字段值
 * 以 JSON 形式存储在 {@code tasks.extended_fields_json} 列中。</p>
 *
 * <p><strong>主键约束</strong>：字段 {@code fieldKey} 映射到 DB 列 {@code key}，
 * 是 MySQL 保留字。通过 {@code @Column(name = "`key`")} 使用反引号告知
 * Hibernate 该标识符始终需要引用（JPA 规范行为），与仓库内
 * {@code ProjectQualityCheck.empty} 的用法一致。</p>
 *
 * <p>时间戳字段 {@code created_at} / {@code updated_at} 通过
 * {@link PrePersist} / {@link PreUpdate} 回调由 JPA 层维护。</p>
 */
@Entity
@Table(name = "task_extended_field")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskExtendedField {

    /** Column length for {@code key} (also primary key). */
    private static final int LEN_KEY = 64;
    /** Column length for {@code label} (display name). */
    private static final int LEN_LABEL = 128;
    /** Column length for {@code field_type} (enum string). */
    private static final int LEN_FIELD_TYPE = 32;
    /** Column length for {@code placeholder}. */
    private static final int LEN_PLACEHOLDER = 255;

    /**
     * 字段 key（逻辑主键），如 {@code customer_code}、{@code delivery_date}。
     *
     * <p>DB 列名为 {@code key}（MySQL 保留字），通过反引号引用标识符。
     * 一旦落库不可修改，业务层禁止修改既有 key。</p>
     */
    @Id
    @Column(name = "`key`", nullable = false, length = LEN_KEY)
    private String fieldKey;

    /** 显示名（中文或多语言字符串），如"客户编号"。 */
    @NotNull
    @Column(name = "label", nullable = false, length = LEN_LABEL)
    private String label;

    /** 字段类型，持久化为字符串以匹配 V103 CHECK 约束。 */
    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "field_type", nullable = false, length = LEN_FIELD_TYPE)
    private TaskExtendedFieldType fieldType;

    /** 是否必填。 */
    @NotNull
    @Column(name = "required", nullable = false)
    private Boolean required = Boolean.FALSE;

    /** 输入占位提示。 */
    @Column(name = "placeholder", length = LEN_PLACEHOLDER)
    private String placeholder;

    /** 下拉选项（JSON 数组），仅当 fieldType = select 时有值。 */
    @Column(name = "options_json", columnDefinition = "TEXT")
    private String optionsJson;

    /** 排序字段，升序。 */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /** 是否启用（停用后不再出现在任务表单中）。 */
    @NotNull
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;

    /** 创建时间（由 {@link PrePersist} 回调填充）。 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 更新时间（由 {@link PrePersist} / {@link PreUpdate} 回调维护）。 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** 插入前自动设置创建与更新时间。 */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    /** 更新前自动刷新更新时间。 */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
