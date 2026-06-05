package com.xiyu.bid.biddraftagent.repository;

import com.xiyu.bid.biddraftagent.entity.BidAgentRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BidAgentRunRepository extends JpaRepository<BidAgentRun, Long> {

    Optional<BidAgentRun> findByIdAndProjectId(Long id, Long projectId);

    List<BidAgentRun> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    Optional<BidAgentRun> findTopByProjectIdOrderByCreatedAtDesc(Long projectId);
}
