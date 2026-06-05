// Input: Subscription entity queries
// Output: repository abstractions for persistence
// Pos: Repository/订阅数据访问
package com.xiyu.bid.subscription.repository;

import com.xiyu.bid.subscription.entity.Subscription;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findByUserIdAndTargetEntityTypeAndTargetEntityId(
        Long userId, String targetEntityType, Long targetEntityId);

    List<Subscription> findByTargetEntityTypeAndTargetEntityId(
        String targetEntityType, Long targetEntityId);

    Page<Subscription> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    boolean existsByUserIdAndTargetEntityTypeAndTargetEntityId(
        Long userId, String targetEntityType, Long targetEntityId);

    @Modifying
    @Query("DELETE FROM Subscription s WHERE s.userId = :userId AND s.targetEntityType = :type AND s.targetEntityId = :id")
    int deleteByUserIdAndTarget(@Param("userId") Long userId, @Param("type") String type, @Param("id") Long id);
}
