package com.xiyu.bid.integration.organization.application;

import com.xiyu.bid.integration.organization.domain.OrganizationEventNotice;
import com.xiyu.bid.integration.organization.domain.OrganizationEventStatus;
import com.xiyu.bid.integration.organization.domain.OrganizationDirectoryRetryPolicy;
import com.xiyu.bid.integration.organization.domain.OrganizationRetryDecision;
import com.xiyu.bid.integration.organization.domain.OrganizationSyncPolicy;
import com.xiyu.bid.integration.organization.infrastructure.persistence.entity.OrganizationEventLogEntity;
import com.xiyu.bid.integration.organization.infrastructure.persistence.repository.OrganizationEventLogRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

@Service
public class OrganizationEventInboxService {
    private static final String RETRYING_MESSAGE = "retrying";
    private static final String PROCESSING_LEASE_EXPIRED_MESSAGE = "processing lease expired";
    private static final String MANUAL_REPLAYING_MESSAGE = "manual replaying";
    private static final String MANUAL_REPLAY_LEASE_EXPIRED_MESSAGE = "manual replay lease expired";

    private final OrganizationEventLogRepository eventLogRepository;
    private final OrganizationIntegrationProperties properties;

    public OrganizationEventInboxService(
            OrganizationEventLogRepository eventLogRepository,
            OrganizationIntegrationProperties properties
    ) {
        this.eventLogRepository = eventLogRepository;
        this.properties = properties;
    }

    public String eventKey(OrganizationEventNotice notice) {
        return OrganizationEventKeyFactory.hash(OrganizationSyncPolicy.idempotencyKey(notice));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean reserve(OrganizationEventNotice notice, String rawPayload) {
        try {
            eventLogRepository.saveAndFlush(buildLog(notice, rawPayload, OrganizationEventStatus.PROCESSING, "processing", ""));
            return true;
        } catch (DataIntegrityViolationException ex) {
            return false;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRejected(String eventKey, String message, String rawPayload) {
        OrganizationEventLogEntity log = eventLogRepository.findByEventKey(eventKey).orElseGet(() -> rejectedLog(eventKey, rawPayload));
        applyStatus(log, OrganizationEventStatus.REJECTED, message, "VALIDATION_FAILED");
        eventLogRepository.save(log);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessed(String eventKey) {
        updateStatus(eventKey, OrganizationEventStatus.PROCESSED, "success", "");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String eventKey, String message, String errorCode) {
        markRetryableFailure(eventKey, message, errorCode, configuredMaxAttempts());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRetryableFailure(String eventKey, String message, String errorCode, int maxAttempts) {
        eventLogRepository.findByEventKey(eventKey).ifPresent(log -> {
            LocalDateTime now = LocalDateTime.now();
            int retryCount = log.getRetryCount() == null ? 1 : log.getRetryCount() + 1;
            OrganizationRetryDecision decision = OrganizationDirectoryRetryPolicy.decide(retryCount, maxAttempts, now);
            log.setStatus(decision.status());
            log.setMessage(message == null ? "" : message);
            log.setLastErrorCode(errorCode == null ? "" : errorCode);
            log.setRetryCount(retryCount);
            log.setNextRetryAt(decision.nextRetryAt());
            log.setProcessedAt(now);
            eventLogRepository.save(log);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markNonRetryableFailure(String eventKey, String message, String errorCode) {
        eventLogRepository.findByEventKey(eventKey).ifPresent(log -> {
            applyStatus(log, OrganizationEventStatus.DEAD_LETTER, message, errorCode);
            log.setNextRetryAt(null);
            eventLogRepository.save(log);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claimDueRetry(String eventKey, LocalDateTime now) {
        return eventLogRepository.claimDueRetry(
                eventKey,
                OrganizationEventStatus.PENDING_RETRY,
                OrganizationEventStatus.PROCESSING,
                now,
                RETRYING_MESSAGE
        ) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recoverStaleProcessing(LocalDateTime cutoff, LocalDateTime now) {
        int retryRecovered = eventLogRepository.recoverStaleProcessing(
                OrganizationEventStatus.PROCESSING,
                OrganizationEventStatus.PENDING_RETRY,
                cutoff,
                now,
                PROCESSING_LEASE_EXPIRED_MESSAGE,
                MANUAL_REPLAYING_MESSAGE
        );
        int replayRecovered = eventLogRepository.recoverStaleDeadLetterReplay(
                OrganizationEventStatus.PROCESSING,
                OrganizationEventStatus.DEAD_LETTER,
                cutoff,
                now,
                MANUAL_REPLAYING_MESSAGE,
                MANUAL_REPLAY_LEASE_EXPIRED_MESSAGE
        );
        return retryRecovered + replayRecovered;
    }

    @Transactional(readOnly = true)
    public List<OrganizationEventLogEntity> findDueRetries(LocalDateTime now, int batchSize) {
        return eventLogRepository.findByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                OrganizationEventStatus.PENDING_RETRY,
                now,
                PageRequest.of(0, batchSize)
        );
    }

    @Transactional(readOnly = true)
    public Optional<OrganizationEventLogEntity> findByEventKey(String eventKey) {
        return eventLogRepository.findByEventKey(eventKey);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claimDeadLetterReplay(String eventKey, LocalDateTime now) {
        return eventLogRepository.claimDeadLetterReplay(
                eventKey,
                OrganizationEventStatus.DEAD_LETTER,
                OrganizationEventStatus.PROCESSING,
                now,
                MANUAL_REPLAYING_MESSAGE
        ) == 1;
    }

    private void updateStatus(String eventKey, OrganizationEventStatus status, String message, String errorCode) {
        eventLogRepository.findByEventKey(eventKey).ifPresent(log -> {
            applyStatus(log, status, message, errorCode);
            eventLogRepository.save(log);
        });
    }

    private int configuredMaxAttempts() {
        return Math.max(1, properties.getRetry().getMaxAttempts());
    }

    private OrganizationEventLogEntity rejectedLog(String eventKey, String rawPayload) {
        OrganizationEventLogEntity log = new OrganizationEventLogEntity();
        log.setEventKey(eventKey);
        log.setEventTopic("");
        log.setSourceApp("");
        log.setTraceId("");
        log.setPayloadHash(OrganizationEventKeyFactory.hash(rawPayload == null ? "" : rawPayload));
        log.setRawPayload(rawPayload);
        return log;
    }

    private OrganizationEventLogEntity buildLog(
            OrganizationEventNotice notice,
            String rawPayload,
            OrganizationEventStatus status,
            String message,
            String errorCode
    ) {
        OrganizationEventLogEntity log = new OrganizationEventLogEntity();
        log.setEventKey(eventKey(notice));
        log.setUpstreamEventKey(notice.key());
        log.setEventTopic(notice.topic().topic());
        log.setSourceApp(notice.eventSource());
        log.setTraceId(notice.traceId());
        log.setSpanId(notice.spanId());
        log.setParentId(notice.parentId());
        log.setEventTime(parseEventTime(notice.time()));
        log.setEntityType(notice.topic().entityType());
        if (notice.topic().entityType().equals("USER")) {
            log.setExternalUserId(notice.subjectId());
        } else {
            log.setExternalDeptId(notice.subjectId());
        }
        log.setPayloadHash(OrganizationEventKeyFactory.hash(rawPayload));
        log.setRawPayload(rawPayload);
        applyStatus(log, status, message, errorCode);
        return log;
    }

    private void applyStatus(OrganizationEventLogEntity log, OrganizationEventStatus status, String message, String errorCode) {
        log.setStatus(status);
        log.setMessage(message == null ? "" : message);
        log.setLastErrorCode(errorCode == null ? "" : errorCode);
        log.setProcessedAt(LocalDateTime.now());
        if (status == OrganizationEventStatus.FAILED) {
            log.setRetryCount(log.getRetryCount() == null ? 1 : log.getRetryCount() + 1);
            log.setNextRetryAt(LocalDateTime.now().plusMinutes(5));
        }
    }

    private LocalDateTime parseEventTime(String eventTime) {
        if (eventTime == null || eventTime.isBlank()) {
            return null;
        }
        String trimmed = eventTime.trim();
        if (trimmed.chars().allMatch(Character::isDigit)) {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(trimmed)), ZoneId.systemDefault());
        }
        try {
            return OffsetDateTime.parse(trimmed).toLocalDateTime();
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
