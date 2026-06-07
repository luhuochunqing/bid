package com.xiyu.bid.webhook.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WebhookDeliveryLogRepository extends JpaRepository<WebhookDeliveryLog, Long> {
}
