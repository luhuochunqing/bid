package com.xiyu.bid.workflowform.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "oa_process_bindings")
@Getter
@Setter
public class OaProcessBindingEntity {
    @Id
    @Column(name = "template_code", length = 80)
    private String templateCode;

    @Column(name = "provider", nullable = false, length = 32)
    private String provider;

    @Column(name = "workflow_code", nullable = false, length = 120)
    private String workflowCode;

    @Column(name = "field_mapping_json", columnDefinition = "TEXT")
    private String fieldMappingJson;

    @Column(nullable = false)
    private boolean enabled;
}
