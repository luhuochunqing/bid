package com.xiyu.bid.bidmatch.infrastructure.persistence.repository;

import com.xiyu.bid.bidmatch.infrastructure.persistence.entity.BidMatchScoreEvaluationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BidMatchScoreEvaluationJpaRepository extends JpaRepository<BidMatchScoreEvaluationEntity, Long> {
    Optional<BidMatchScoreEvaluationEntity> findFirstByTenderIdOrderByEvaluatedAtDescIdDesc(Long tenderId);

    List<BidMatchScoreEvaluationEntity> findByTenderIdOrderByEvaluatedAtDescIdDesc(Long tenderId);
}
