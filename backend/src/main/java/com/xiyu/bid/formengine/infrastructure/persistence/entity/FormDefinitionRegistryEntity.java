// Input: JPA Entity — form_definition_registry 表映射
// Output: 表单定义注册实体
// Pos: Infrastructure/Persistence 层
// 维护声明: 仅维护 JPA 映射与数据库约束；业务规则下沉到 domain/application.
package com.xiyu.bid.formengine.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "form_definition_registry",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_scope_org", columnNames = {"scope", "org_id"})
        },
        indexes = {
                @Index(name = "idx_scope", columnList = "scope"),
                @Index(name = "idx_org_id", columnList = "org_id")
        }
)
@Getter
@Setter
public class FormDefinitionRegistryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String scope;

    @Column(name = "scope_label", nullable = false, length = 128)
    private String scopeLabel;

    @Column(nullable = false)
    private Integer version;

    @Column(name = "schema_json", nullable = false, columnDefinition = "JSON")
    private String schemaJson;

    @Column(nullable = false)
    private Boolean enabled;

    @Column(name = "org_id")
    private Long orgId;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
