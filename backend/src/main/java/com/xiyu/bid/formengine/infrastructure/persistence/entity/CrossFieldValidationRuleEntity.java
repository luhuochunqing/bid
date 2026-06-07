// Input: JPA Entity — cross_field_validation_rule 表映射
// Output: 跨字段验证规则实体
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
        name = "cross_field_validation_rule",
        indexes = {
                @Index(name = "idx_cfvr_definition_id", columnList = "definition_id"),
                @Index(name = "idx_cfvr_scope", columnList = "scope")
        }
)
@Getter
@Setter
public class CrossFieldValidationRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "definition_id", nullable = false,
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_cfvr_def"))
    private FormDefinitionRegistryEntity definition;

    @Column(name = "scope", nullable = false, length = 100)
    private String scope;

    @Column(name = "source_field", nullable = false, length = 100)
    private String sourceField;

    @Column(name = "operator", nullable = false, length = 50)
    private String operator;

    @Column(name = "target_field", length = 100)
    private String targetField;

    @Column(name = "target_value", length = 500)
    private String targetValue;

    @Column(name = "error_message", nullable = false, length = 500)
    private String errorMessage;

    @Column(nullable = false)
    private Integer priority;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
