package com.xiyu.bid.webhook.infrastructure;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface WebhookDeliveryTaskRepository extends JpaRepository<WebhookDeliveryTask, Long> {
    default List<WebhookDeliveryTask> findRunnableTasks(LocalDateTime now, int batchSize) {
        return findByStatusInAndNextRetryAtLessThanEqualOrderByCreatedAtAsc(
                List.of(WebhookDeliveryTaskStatus.PENDING_RETRY),
                now,
                PageRequest.of(0, batchSize)
        );
    }

    List<WebhookDeliveryTask> findByStatusInAndNextRetryAtLessThanEqualOrderByCreatedAtAsc(
            List<WebhookDeliveryTaskStatus> statuses,
            LocalDateTime now,
            PageRequest pageRequest
    );
}
