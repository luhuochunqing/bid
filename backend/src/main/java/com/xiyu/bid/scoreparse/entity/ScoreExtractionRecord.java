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

/** Immutable once frozen; separate from task avoids detached task status updates losing the snapshot. */
@Entity @Table(name="score_extraction_snapshot") @Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ScoreExtractionRecord {
    @Id @Column(name="parse_task_id") private Long parseTaskId;
    @Column(name="project_id",nullable=false) private Long projectId;
    @Column(name="base_fingerprint",nullable=false,length=128) private String baseFingerprint;
    @Column(name="payload",columnDefinition="JSON") private String payload;
    @Column(name="frozen_at") private LocalDateTime frozenAt;
}
