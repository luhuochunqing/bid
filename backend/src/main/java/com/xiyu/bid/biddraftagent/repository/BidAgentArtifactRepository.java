package com.xiyu.bid.biddraftagent.repository;

import com.xiyu.bid.biddraftagent.entity.BidAgentArtifact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BidAgentArtifactRepository extends JpaRepository<BidAgentArtifact, Long> {

    List<BidAgentArtifact> findByRunIdOrderByCreatedAtAsc(Long runId);

    Optional<BidAgentArtifact> findByRunIdAndArtifactType(Long runId, String artifactType);
}
