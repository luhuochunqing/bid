// Input: JPA Entity — form_field_condition 表映射
// Output: 字段条件逻辑实体
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
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "form_field_condition",
        indexes = {
                @Index(name = "idx_ffc_definition_id", columnList = "definition_id"),
                @Index(name = "idx_ffc_source_field", columnList = "source_field")
        }
)
@Getter
@Setter
public class FormFieldConditionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "definition_id", nullable = false,
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_condition_def"))
    private FormDefinitionRegistryEntity definition;

    @Column(name = "source_field", nullable = false, length = 64)
    private String sourceField;

    @Column(nullable = false, length = 32)
    private String operator;

    @Column(name = "target_value", length = 255)
    private String targetValue;

    @Column(nullable = false, length = 32)
    private String action;

    @Column(name = "target_field", length = 64)
    private String targetField;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
