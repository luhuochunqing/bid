package com.xiyu.bid.ai.repository;

import com.xiyu.bid.ai.entity.AiAnalysisJob;
import com.xiyu.bid.ai.entity.AiAnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiAnalysisResultRepository extends JpaRepository<AiAnalysisResult, Long> {

    Optional<AiAnalysisResult> findFirstByTenderIdAndAnalysisTypeOrderByCreatedAtDesc(
            Long tenderId,
            AiAnalysisJob.AnalysisType analysisType
    );

    Optional<AiAnalysisResult> findFirstByProjectIdAndAnalysisTypeOrderByCreatedAtDesc(
            Long projectId,
            AiAnalysisJob.AnalysisType analysisType
    );
}
