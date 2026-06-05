// Input: TenderKeywordSubscription entity queries
// Output: repository abstractions for keyword subscription persistence
// Pos: Repository/标讯关键词订阅数据访问
package com.xiyu.bid.tenderkeyword.repository;

import com.xiyu.bid.tenderkeyword.entity.TenderKeywordSubscription;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TenderKeywordSubscriptionRepository extends JpaRepository<TenderKeywordSubscription, Long> {

    Page<TenderKeywordSubscription> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<TenderKeywordSubscription> findByUserId(Long userId);

    long countByUserId(Long userId);

    @Query("SELECT s FROM TenderKeywordSubscription s WHERE s.status = 'ACTIVE'")
    List<TenderKeywordSubscription> findAllActive();
}
