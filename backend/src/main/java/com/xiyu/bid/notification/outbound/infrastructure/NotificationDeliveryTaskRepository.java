package com.xiyu.bid.notification.outbound.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationDeliveryTaskRepository extends JpaRepository<NotificationDeliveryTask, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from NotificationDeliveryTask t where t.id = :id")
    Optional<NotificationDeliveryTask> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select t from NotificationDeliveryTask t
            where (t.status = com.xiyu.bid.notification.outbound.infrastructure.NotificationDeliveryTaskStatus.PENDING
                   or (t.status = com.xiyu.bid.notification.outbound.infrastructure.NotificationDeliveryTaskStatus.PENDING_RETRY
                       and (t.nextRetryAt is null or t.nextRetryAt <= :now)))
            order by t.id asc
            """)
    List<NotificationDeliveryTask> findRunnableTasks(@Param("now") LocalDateTime now);
}
