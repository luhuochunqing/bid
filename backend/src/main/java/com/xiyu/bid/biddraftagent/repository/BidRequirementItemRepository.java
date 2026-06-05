package com.xiyu.bid.biddraftagent.repository;

import com.xiyu.bid.biddraftagent.entity.BidRequirementItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BidRequirementItemRepository extends JpaRepository<BidRequirementItem, Long> {

    List<BidRequirementItem> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    List<BidRequirementItem> findByProjectIdAndProjectDocumentIdOrderByCreatedAtDesc(
            Long projectId,
            Long projectDocumentId
    );
}
