package com.xiyu.bid.notification.outbound.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationDeliveryDlqRepository extends JpaRepository<NotificationDeliveryDlq, Long> {
}
