// Input: TenderKeywordSubscriptionKeyword entity queries
// Output: repository abstractions for keyword persistence
// Pos: Repository/订阅关键词数据访问
package com.xiyu.bid.tenderkeyword.repository;

import com.xiyu.bid.tenderkeyword.entity.TenderKeywordSubscriptionKeyword;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TenderKeywordSubscriptionKeywordRepository
        extends JpaRepository<TenderKeywordSubscriptionKeyword, Long> {

    List<TenderKeywordSubscriptionKeyword> findBySubscriptionId(Long subscriptionId);

    @Modifying
    @Query("DELETE FROM TenderKeywordSubscriptionKeyword k WHERE k.subscriptionId = :subscriptionId")
    void deleteBySubscriptionId(@Param("subscriptionId") Long subscriptionId);

    @Modifying
    @Query("DELETE FROM TenderKeywordSubscriptionKeyword k WHERE k.subscriptionId IN " +
           "(SELECT s.id FROM TenderKeywordSubscription s WHERE s.userId = :userId)")
    void deleteByUserId(@Param("userId") Long userId);
}
