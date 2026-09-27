package com.xiyu.bid.scoreparse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import java.time.LocalDateTime;

/** Append-only activation history. Latest ID under the project row lock is authoritative. */
@Entity @Table(name="score_active_version") @Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ScoreActiveVersion {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="project_id",nullable=false) private Long projectId;
    @Column(name="parse_task_id",nullable=false) private Long parseTaskId;
    @Column(name="scheme_id",nullable=false,length=255) private String schemeId;
    @Column(name="previous_version_id") private Long previousVersionId;
    @Column(name="projection_fingerprint",nullable=false,length=64) private String projectionFingerprint;
    @Column(name="selection_payload",nullable=false,columnDefinition="JSON") private String selectionPayload;
    @Column(name="activation_source",nullable=false,length=16) private String activationSource;
    @Column(name="has_manual_edits",nullable=false) private boolean hasManualEdits;
    @Column(name="reason",nullable=false,columnDefinition="TEXT") private String reason;
    @Column(name="operator_name",length=200) private String operatorName;
    @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
}
