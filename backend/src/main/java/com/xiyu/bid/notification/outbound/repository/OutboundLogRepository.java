package com.xiyu.bid.notification.outbound.repository;

import com.xiyu.bid.notification.outbound.entity.OutboundLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboundLogRepository extends JpaRepository<OutboundLog, Long> {

    List<OutboundLog> findByNotificationId(Long notificationId);

    List<OutboundLog> findByUserIdOrderByCreatedAtDesc(Long userId);
}
