package com.xiyu.bid.casework.infrastructure;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "archive_log")
public class ArchiveLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "archive_id", nullable = false)
    private Long archiveId;

    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    @Column(name = "operator_name", nullable = false)
    private String operatorName;

    @Column(name = "action_type", nullable = false)
    private String actionType; // PREVIEW, DOWNLOAD, EXPORT

    @Column(name = "action_content", nullable = false)
    private String actionContent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
