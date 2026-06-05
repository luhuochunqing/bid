package com.xiyu.bid.notification.repository;

import com.xiyu.bid.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    boolean existsBySourceEntityTypeAndSourceEntityIdAndCreatedAtAfter(String sourceEntityType, Long sourceEntityId, java.time.LocalDateTime createdAt);
}
