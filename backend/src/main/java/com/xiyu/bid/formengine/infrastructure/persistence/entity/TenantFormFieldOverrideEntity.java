// Input: JPA Entity — tenant_form_field_override 表映射
// Output: 租户字段覆盖实体
// Pos: Infrastructure/Persistence 层
// 维护声明: 仅维护 JPA 映射与唯一约束；业务逻辑在 application 层.
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
        name = "tenant_form_field_override",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_def_org_field_type",
                        columnNames = {"definition_id", "org_id", "field_key", "override_type"})
        },
        indexes = {
                @Index(name = "idx_tfo_definition_id", columnList = "definition_id"),
                @Index(name = "idx_tfo_org_id", columnList = "org_id")
        }
)
@Getter
@Setter
public class TenantFormFieldOverrideEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "definition_id", nullable = false,
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_tfo_def"))
    private FormDefinitionRegistryEntity definition;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @Column(name = "field_key", nullable = false, length = 100)
    private String fieldKey;

    @Column(name = "override_type", nullable = false, length = 50)
    private String overrideType;

    @Column(name = "override_value", columnDefinition = "TEXT")
    private String overrideValue;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
