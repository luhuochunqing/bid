package com.xiyu.bid.bidmatch.infrastructure.persistence.repository;

import com.xiyu.bid.bidmatch.infrastructure.persistence.entity.BidMatchScoringModelEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BidMatchScoringModelJpaRepository extends JpaRepository<BidMatchScoringModelEntity, Long> {
    List<BidMatchScoringModelEntity> findAllByOrderByIdAsc();

    Optional<BidMatchScoringModelEntity> findFirstByStatusOrderByIdAsc(String status);
}
