// Input: projectId -> BidTenderDocumentSnapshot + KnowledgeBaseMatchAppService -> BidScoreEvaluationPolicy
// Output: 投标文件实际打分结果 EvaluationResult
// Pos: biddraftagent/application — 投标文件实际打分应用服务
package com.xiyu.bid.biddraftagent.application;

import com.xiyu.bid.biddraftagent.domain.BidScoreEvaluationPolicy;
import com.xiyu.bid.biddraftagent.domain.ScoringCriterion;
import com.xiyu.bid.biddraftagent.domain.TenderRequirementProfile;
import com.xiyu.bid.biddraftagent.domain.validation.KnowledgeBaseMatchResult;
import com.xiyu.bid.biddraftagent.repository.BidTenderDocumentSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BidScoreEvaluationAppService {

    private static final String DEFAULT_BID_FILE = "西域数智化投标文件_v3.pdf";

    private final BidTenderDocumentSnapshotRepository snapshotRepository;
    private final KnowledgeBaseMatchAppService knowledgeBaseMatchAppService;
    private final BidDraftAgentJsonCodec jsonCodec;
    private final com.xiyu.bid.service.ProjectAccessScopeService projectAccessScopeService;
    private final BidScoreEvaluationPolicy evaluationPolicy = new BidScoreEvaluationPolicy();

    public BidScoreEvaluationPolicy.EvaluationResult evaluateForProject(Long projectId) {
        log.info("BidScoreEvaluationAppService: evaluating score for projectId={}", projectId);
        if (projectAccessScopeService != null) {
            projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        }
        var snapshot = snapshotRepository
                .findTopByProjectIdOrderByCreatedAtDescIdDesc(projectId)
                .orElse(null);

        if (snapshot == null || snapshot.getProfileJson() == null) {
            return new BidScoreEvaluationPolicy.EvaluationResult(
                    Collections.emptyList(),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    DEFAULT_BID_FILE,
                    LocalDateTime.now()
            );
        }

        TenderRequirementProfile profile = jsonCodec.fromJson(
                snapshot.getProfileJson(), TenderRequirementProfile.class);

        if (profile == null || profile.scoringCriteriaItems() == null || profile.scoringCriteriaItems().isEmpty()) {
            return new BidScoreEvaluationPolicy.EvaluationResult(
                    Collections.emptyList(),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    DEFAULT_BID_FILE,
                    LocalDateTime.now()
            );
        }

        List<ScoringCriterion> criteria = profile.scoringCriteriaItems();
        KnowledgeBaseMatchResult kbMatch = knowledgeBaseMatchAppService.matchForProject(projectId);
        String bidFileName = DEFAULT_BID_FILE;

        return evaluationPolicy.evaluate(criteria, kbMatch, bidFileName);
    }
}
