package com.xiyu.bid.casework.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KnowledgeCaseRepository extends JpaRepository<KnowledgeCase, Long>, JpaSpecificationExecutor<KnowledgeCase> {

    List<KnowledgeCase> findBySourceProjectId(Long sourceProjectId);

    List<KnowledgeCase> findByStatus(String status);

    boolean existsBySourceProjectIdAndScoringPointTitle(Long sourceProjectId, String scoringPointTitle);
}

