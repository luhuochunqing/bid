package com.xiyu.bid.notification.repository;

import com.xiyu.bid.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    boolean existsBySourceEntityTypeAndSourceEntityIdAndCreatedAtAfter(String sourceEntityType, Long sourceEntityId, LocalDateTime createdAt);

    /**
     * 查询指定源实体、类型且在指定时间之后创建的通知，按创建时间降序。
     * 用于 5 分钟滑动窗口去重。
     */
    List<Notification> findBySourceEntityTypeAndSourceEntityIdAndTypeAndCreatedAtAfter(
            String sourceEntityType, Long sourceEntityId, String type, LocalDateTime createdAt);

    /**
     * 查询指定源实体、类型的全部通知，按创建时间降序。
     * 由应用服务自行截取窗口并调用去重策略。
     */
    List<Notification> findBySourceEntityTypeAndSourceEntityIdAndTypeOrderByCreatedAtDesc(
            String sourceEntityType, Long sourceEntityId, String type);

    boolean existsBySourceEntityTypeAndSourceEntityIdAndType(
            String sourceEntityType, Long sourceEntityId, String type);

    @Query("""
            select notification.sourceEntityId
            from Notification notification
            where notification.sourceEntityType = :sourceEntityType
              and notification.type = :type
              and notification.sourceEntityId in :sourceEntityIds
            """)
    List<Long> findSourceEntityIds(
            @Param("sourceEntityType") String sourceEntityType,
            @Param("type") String type,
            @Param("sourceEntityIds") Collection<Long> sourceEntityIds);
}
