package com.xiyu.bid.contractborrow.infrastructure.persistence.entity;

import com.xiyu.bid.contractborrow.domain.valueobject.ContractBorrowEventType;
import com.xiyu.bid.contractborrow.domain.valueobject.ContractBorrowStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "contract_borrow_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractBorrowEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private ContractBorrowEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_after", nullable = false, length = 32)
    private ContractBorrowStatus statusAfter;

    @Column(name = "actor_name", length = 100)
    private String actorName;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
