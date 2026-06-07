package com.xiyu.bid.workflowform.infrastructure.persistence.entity;

import com.xiyu.bid.workflowform.domain.FormBusinessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "workflow_form_templates")
@Getter
@Setter
public class WorkflowFormTemplateEntity {
    @Id
    @Column(name = "template_code", length = 80)
    private String templateCode;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "business_type", nullable = false, length = 64)
    private FormBusinessType businessType;

    @Column(nullable = false)
    private Integer version;

    @Column(name = "schema_json", nullable = false, columnDefinition = "TEXT")
    private String schemaJson;

    @Column(nullable = false)
    private boolean enabled;
}
