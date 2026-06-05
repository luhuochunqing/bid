// Input: JPA Entity — form_submission_audit 表映射
// Output: 表单提交审计实体
// Pos: Infrastructure/Persistence 层
// 维护声明: 仅维护 JPA 映射与外键约束；业务逻辑在 application 层.
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
        name = "form_submission_audit",
        indexes = {
                @Index(name = "idx_fsa_definition_id", columnList = "definition_id"),
                @Index(name = "idx_fsa_operator", columnList = "operator_username"),
                @Index(name = "idx_fsa_org_id", columnList = "org_id"),
                @Index(name = "idx_fsa_created_at", columnList = "created_at")
        }
)
@Getter
@Setter
public class FormSubmissionAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "definition_id", nullable = false,
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_fsa_def"))
    private FormDefinitionRegistryEntity definition;

    @Column(nullable = false, length = 100)
    private String scope;

    @Column(name = "operator_username", nullable = false, length = 100)
    private String operatorUsername;

    @Column(name = "org_id")
    private Long orgId;

    @Column(name = "form_data_hash", nullable = false, length = 64)
    private String formDataHash;

    @Column(name = "form_data_snapshot", columnDefinition = "TEXT")
    private String formDataSnapshot;

    @Column(nullable = false, length = 20)
    @jakarta.persistence.Convert(converter = FormSubmissionAuditStatusConverter.class)
    private String status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * 提交状态常量。
     * 与数据库 VARCHAR(20) 列保持一致，不使用 JPA @Enumerated。
     */
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String STATUS_PROCESSING_ERROR = "PROCESSING_ERROR";

    @jakarta.persistence.Converter
    public static class FormSubmissionAuditStatusConverter implements jakarta.persistence.AttributeConverter<String, String> {
        @Override
        public String convertToDatabaseColumn(String attribute) {
            if (attribute == null) {
                return null;
            }
            return attribute.toLowerCase();
        }

        @Override
        public String convertToEntityAttribute(String dbData) {
            if (dbData == null) {
                return null;
            }
            return dbData.toUpperCase();
        }
    }
}
