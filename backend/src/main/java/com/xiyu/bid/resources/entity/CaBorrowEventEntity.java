package com.xiyu.bid.resources.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ca_borrow_events")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class CaBorrowEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "event_type", length = 30, nullable = false)
    private String eventType;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Column(name = "actor_name", length = 100, nullable = false)
    private String actorName;

    @Column(length = 500)
    private String comment;

    @Column(name = "status_before", length = 30)
    private String statusBefore;

    @Column(name = "status_after", length = 30, nullable = false)
    private String statusAfter;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
