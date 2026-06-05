package com.xiyu.bid.integration.organization.infrastructure.persistence.repository;

import com.xiyu.bid.integration.organization.infrastructure.persistence.entity.OrganizationEventLogEntity;
import com.xiyu.bid.integration.organization.domain.OrganizationEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrganizationEventLogRepository extends JpaRepository<OrganizationEventLogEntity, Long> {
    boolean existsByEventKey(String eventKey);

    Optional<OrganizationEventLogEntity> findByEventKey(String eventKey);

    long countByStatus(OrganizationEventStatus status);

    List<OrganizationEventLogEntity> findByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
            OrganizationEventStatus status,
            LocalDateTime nextRetryAt,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update OrganizationEventLogEntity event
            set event.status = :processingStatus,
                event.message = :message,
                event.nextRetryAt = null,
                event.processedAt = :now
            where event.eventKey = :eventKey
              and event.status = :pendingStatus
              and event.nextRetryAt <= :now
            """)
    int claimDueRetry(
            @Param("eventKey") String eventKey,
            @Param("pendingStatus") OrganizationEventStatus pendingStatus,
            @Param("processingStatus") OrganizationEventStatus processingStatus,
            @Param("now") LocalDateTime now,
            @Param("message") String message
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update OrganizationEventLogEntity event
            set event.status = :processingStatus,
                event.message = :message,
                event.nextRetryAt = null,
                event.processedAt = :now
            where event.eventKey = :eventKey
              and event.status = :deadLetterStatus
            """)
    int claimDeadLetterReplay(
            @Param("eventKey") String eventKey,
            @Param("deadLetterStatus") OrganizationEventStatus deadLetterStatus,
            @Param("processingStatus") OrganizationEventStatus processingStatus,
            @Param("now") LocalDateTime now,
            @Param("message") String message
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update OrganizationEventLogEntity event
            set event.status = :pendingStatus,
                event.message = :message,
                event.nextRetryAt = :now
            where event.status = :processingStatus
              and (event.message is null or event.message <> :excludedMessage)
              and event.processedAt <= :cutoff
            """)
    int recoverStaleProcessing(
            @Param("processingStatus") OrganizationEventStatus processingStatus,
            @Param("pendingStatus") OrganizationEventStatus pendingStatus,
            @Param("cutoff") LocalDateTime cutoff,
            @Param("now") LocalDateTime now,
            @Param("message") String message,
            @Param("excludedMessage") String excludedMessage
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update OrganizationEventLogEntity event
            set event.status = :deadLetterStatus,
                event.message = :message,
                event.nextRetryAt = null,
                event.processedAt = :now
            where event.status = :processingStatus
              and event.message = :replayMessage
              and event.processedAt <= :cutoff
            """)
    int recoverStaleDeadLetterReplay(
            @Param("processingStatus") OrganizationEventStatus processingStatus,
            @Param("deadLetterStatus") OrganizationEventStatus deadLetterStatus,
            @Param("cutoff") LocalDateTime cutoff,
            @Param("now") LocalDateTime now,
            @Param("replayMessage") String replayMessage,
            @Param("message") String message
    );

    int deleteByReceivedAtBefore(LocalDateTime cutoff);
}
