package com.xiyu.bid.workflowform.infrastructure.persistence.entity;

import com.xiyu.bid.workflowform.domain.FormBusinessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "workflow_form_template_versions")
@Getter
@Setter
public class WorkflowFormTemplateVersionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_code", nullable = false, length = 80)
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

    @Column(name = "published_by", length = 120)
    private String publishedBy;

    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;
}
