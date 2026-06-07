// Input: TenderKeywordMatchLog entity queries
// Output: repository abstractions for match log persistence
// Pos: Repository/匹配日志数据访问
package com.xiyu.bid.tenderkeyword.repository;

import com.xiyu.bid.tenderkeyword.entity.TenderKeywordMatchLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface TenderKeywordMatchLogRepository extends JpaRepository<TenderKeywordMatchLog, Long> {

    Page<TenderKeywordMatchLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<TenderKeywordMatchLog> findBySubscriptionIdOrderByCreatedAtDesc(Long subscriptionId, Pageable pageable);

    List<TenderKeywordMatchLog> findBySubscriptionIdAndNotifiedFalse(Long subscriptionId);

    boolean existsByTenderIdAndSubscriptionId(Long tenderId, Long subscriptionId);

    long countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime after);

    @Query("SELECT COALESCE(MAX(l.createdAt), '1970-01-01') FROM TenderKeywordMatchLog l WHERE l.subscriptionId = :subscriptionId")
    LocalDateTime findLastMatchTimeBySubscriptionId(@Param("subscriptionId") Long subscriptionId);

    @Modifying
    @Query("UPDATE TenderKeywordMatchLog l SET l.notified = true, l.notifiedAt = :now " +
           "WHERE l.subscriptionId = :subscriptionId AND l.notified = false")
    int markAsNotified(@Param("subscriptionId") Long subscriptionId, @Param("now") LocalDateTime now);
}
