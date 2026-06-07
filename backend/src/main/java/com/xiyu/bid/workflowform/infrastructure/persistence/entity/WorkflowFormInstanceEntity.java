package com.xiyu.bid.workflowform.infrastructure.persistence.entity;

import com.xiyu.bid.workflowform.domain.FormBusinessType;
import com.xiyu.bid.workflowform.domain.WorkflowFormStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "workflow_form_instances")
@Getter
@Setter
public class WorkflowFormInstanceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "business_type", nullable = false, length = 64)
    private FormBusinessType businessType;

    @Column(name = "template_code", nullable = false, length = 80)
    private String templateCode;

    @Column(name = "template_version")
    private Integer templateVersion;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "applicant_name", length = 120)
    private String applicantName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 32)
    private WorkflowFormStatus status;

    @Column(name = "form_data_json", nullable = false, columnDefinition = "TEXT")
    private String formDataJson;

    @Column(name = "schema_snapshot_json", columnDefinition = "TEXT")
    private String schemaSnapshotJson;

    @Column(name = "oa_binding_snapshot_json", columnDefinition = "TEXT")
    private String oaBindingSnapshotJson;

    @Column(name = "oa_payload_json", columnDefinition = "TEXT")
    private String oaPayloadJson;

    @Column(name = "oa_instance_id", length = 120)
    private String oaInstanceId;

    @Column(name = "business_applied", nullable = false)
    private boolean businessApplied;

    @Column(name = "business_apply_error", length = 500)
    private String businessApplyError;

    @Column(name = "oa_operator_name", length = 120)
    private String oaOperatorName;

    @Column(name = "oa_comment", length = 500)
    private String oaComment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
