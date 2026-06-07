package com.xiyu.bid.integration.organization.infrastructure.persistence.repository;

import com.xiyu.bid.integration.organization.domain.OrganizationEventStatus;
import com.xiyu.bid.integration.organization.infrastructure.persistence.entity.OrganizationEventLogEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("OrganizationEventLogRepository")
class OrganizationEventLogRepositoryTest {
    @Autowired
    private OrganizationEventLogRepository repository;

    @Test
    @DisplayName("recovers stale initial, retry, and dead letter replay processing into separate states")
    void recoverStaleProcessing_keepsInitialRetryAndManualReplaySeparate() {
        LocalDateTime cutoff = LocalDateTime.parse("2026-05-15T09:45:00");
        LocalDateTime now = LocalDateTime.parse("2026-05-15T10:00:00");
        repository.saveAndFlush(log("initial-event", "processing", 0, cutoff.minusMinutes(1)));
        repository.saveAndFlush(log("retry-event", "retrying", 1, cutoff.minusMinutes(1)));
        repository.saveAndFlush(log("dead-letter-event", "manual replaying", 0, cutoff.minusMinutes(1)));

        int retryRecovered = repository.recoverStaleProcessing(
                OrganizationEventStatus.PROCESSING,
                OrganizationEventStatus.PENDING_RETRY,
                cutoff,
                now,
                "processing lease expired",
                "manual replaying"
        );
        int replayRecovered = repository.recoverStaleDeadLetterReplay(
                OrganizationEventStatus.PROCESSING,
                OrganizationEventStatus.DEAD_LETTER,
                cutoff,
                now,
                "manual replaying",
                "manual replay lease expired"
        );

        OrganizationEventLogEntity initialEvent = repository.findByEventKey("initial-event").orElseThrow();
        OrganizationEventLogEntity retryEvent = repository.findByEventKey("retry-event").orElseThrow();
        OrganizationEventLogEntity replayEvent = repository.findByEventKey("dead-letter-event").orElseThrow();
        assertThat(retryRecovered).isEqualTo(2);
        assertThat(replayRecovered).isEqualTo(1);
        assertThat(initialEvent.getStatus()).isEqualTo(OrganizationEventStatus.PENDING_RETRY);
        assertThat(initialEvent.getNextRetryAt()).isEqualTo(now);
        assertThat(retryEvent.getStatus()).isEqualTo(OrganizationEventStatus.PENDING_RETRY);
        assertThat(retryEvent.getNextRetryAt()).isEqualTo(now);
        assertThat(replayEvent.getStatus()).isEqualTo(OrganizationEventStatus.DEAD_LETTER);
        assertThat(replayEvent.getNextRetryAt()).isNull();
    }

    private OrganizationEventLogEntity log(String eventKey, String message, int retryCount, LocalDateTime processedAt) {
        OrganizationEventLogEntity log = new OrganizationEventLogEntity();
        log.setEventKey(eventKey);
        log.setEventTopic("BaseOssUser");
        log.setSourceApp("customer-org");
        log.setTraceId("trace-" + eventKey);
        log.setPayloadHash("hash-" + eventKey);
        log.setStatus(OrganizationEventStatus.PROCESSING);
        log.setRetryCount(retryCount);
        log.setMessage(message);
        log.setProcessedAt(processedAt);
        return log;
    }
}
