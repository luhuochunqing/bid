package com.xiyu.bid.casework.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "case_precipitation_task")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CasePrecipitationTask {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "task_id", nullable = false, unique = true, length = 36)
    private String taskId;
    @Column(name = "project_id", nullable = false)
    private Long projectId;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(length = 50)
    private String stage;
    @Column(name = "trigger_user_id")
    private Long triggerUserId;
    @Column(name = "case_count")
    private Integer caseCount;
    @Column(name = "user_error_message", length = 255)
    private String userErrorMessage;
    @Column(name = "started_at")
    private LocalDateTime startedAt;
    @Column(name = "heartbeat_at")
    private LocalDateTime heartbeatAt;
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist void createTimestamps() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }
    @PreUpdate void updateTimestamp() { updatedAt = LocalDateTime.now(); }
}
