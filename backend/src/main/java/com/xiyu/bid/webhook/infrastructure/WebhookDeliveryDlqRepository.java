package com.xiyu.bid.webhook.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WebhookDeliveryDlqRepository extends JpaRepository<WebhookDeliveryDlq, Long> {
}
