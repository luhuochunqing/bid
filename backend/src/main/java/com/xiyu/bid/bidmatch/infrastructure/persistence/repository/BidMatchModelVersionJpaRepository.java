package com.xiyu.bid.bidmatch.infrastructure.persistence.repository;

import com.xiyu.bid.bidmatch.infrastructure.persistence.entity.BidMatchModelVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BidMatchModelVersionJpaRepository extends JpaRepository<BidMatchModelVersionEntity, Long> {
    Optional<BidMatchModelVersionEntity> findFirstByModelIdAndActiveTrueOrderByVersionNoDesc(Long modelId);

    Optional<BidMatchModelVersionEntity> findFirstByActiveTrueOrderByActivatedAtDescIdDesc();

    List<BidMatchModelVersionEntity> findByActiveTrue();

    long countByModelId(Long modelId);
}
