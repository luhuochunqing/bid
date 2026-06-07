// Input: JPA Entity — form_field_visibility 表映射
// Output: 字段可见性规则实体
// Pos: Infrastructure/Persistence 层
// 维护声明: 仅维护 JPA 映射与外键约束；业务规则下沉到 domain/application.
package com.xiyu.bid.formengine.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "form_field_visibility",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_def_field_role", columnNames = {"definition_id", "field_key", "role_pattern"})
        },
        indexes = {
                @Index(name = "idx_ffv_definition_id", columnList = "definition_id"),
                @Index(name = "idx_ffv_role_pattern", columnList = "role_pattern")
        }
)
@Getter
@Setter
public class FormFieldVisibilityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "definition_id", nullable = false,
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_visibility_def"))
    private FormDefinitionRegistryEntity definition;

    @Column(name = "field_key", nullable = false, length = 64)
    private String fieldKey;

    @Column(name = "role_pattern", length = 64)
    private String rolePattern;

    @Column(name = "org_id")
    private Long orgId;

    @Column(nullable = false)
    private Boolean visible;

    @Column(nullable = false)
    private Boolean readonly;

    @Column(nullable = false)
    private Boolean hidden;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
