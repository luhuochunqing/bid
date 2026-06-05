package com.xiyu.bid.biddraftagent.repository;

import com.xiyu.bid.biddraftagent.entity.BidTenderDocumentSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BidTenderDocumentSnapshotRepository extends JpaRepository<BidTenderDocumentSnapshot, Long> {

    Optional<BidTenderDocumentSnapshot> findByIdAndProjectId(Long id, Long projectId);

    Optional<BidTenderDocumentSnapshot> findTopByProjectIdOrderByCreatedAtDescIdDesc(Long projectId);
}
