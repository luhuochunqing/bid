package com.xiyu.bid.notification.repository;

import com.xiyu.bid.notification.entity.UserNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserNotificationRepository extends JpaRepository<UserNotification, Long> {

    @EntityGraph(attributePaths = "notification")
    Page<UserNotification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(Long userId);

    @EntityGraph(attributePaths = "notification")
    List<UserNotification> findByUserIdAndReadAtIsNull(Long userId);

    Optional<UserNotification> findByNotificationIdAndUserId(Long notificationId, Long userId);

    @Modifying
    @Query("UPDATE UserNotification un SET un.readAt = :now WHERE un.userId = :userId AND un.readAt IS NULL")
    int markAllReadForUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
