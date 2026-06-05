// Input: projectId → BidTenderDocumentSnapshot（profile_json）→ ScoringCriteriaClassificationPolicy
// Output: 结构化评分标准列表（含编号、维度、指标、权重、子类型）+ 总分
// Pos: biddraftagent/application — 评分标准分类应用服务

package com.xiyu.bid.biddraftagent.application;

import com.xiyu.bid.biddraftagent.domain.ScoringCriteriaItem;
import com.xiyu.bid.biddraftagent.domain.ScoringCriterion;
import com.xiyu.bid.biddraftagent.domain.ScoringCriteriaClassificationPolicy;
import com.xiyu.bid.biddraftagent.domain.TenderRequirementProfile;
import com.xiyu.bid.biddraftagent.repository.BidTenderDocumentSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 评分标准分类应用服务。
 * 优先使用 AI 提取的结构化评分标准数据（scoringCriteriaItems），
 * 若无结构化数据则回退到文本分类（scoringCriteria → ScoringCriteriaItem）。
 */
@Service
@RequiredArgsConstructor
public class ScoringCriteriaClassificationAppService {

    private final BidTenderDocumentSnapshotRepository snapshotRepository;
    private final BidDraftAgentJsonCodec jsonCodec;
    private final ScoringCriteriaClassificationPolicy classificationPolicy = new ScoringCriteriaClassificationPolicy();

    public ScoringCriteriaClassificationResult classifyForProject(Long projectId) {
        var snapshot = snapshotRepository
                .findTopByProjectIdOrderByCreatedAtDescIdDesc(projectId)
                .orElse(null);
        if (snapshot == null || snapshot.getProfileJson() == null) {
            return ScoringCriteriaClassificationResult.empty();
        }
        TenderRequirementProfile profile = jsonCodec.fromJson(
                snapshot.getProfileJson(), TenderRequirementProfile.class);
        if (profile == null) {
            return ScoringCriteriaClassificationResult.empty();
        }

        if (profile.scoringCriteriaItems() != null && !profile.scoringCriteriaItems().isEmpty()) {
            List<ScoringCriterion> criteria = profile.scoringCriteriaItems();
            BigDecimal totalScore = ScoringCriterion.calculateTotalScore(criteria);
            return new ScoringCriteriaClassificationResult(criteria, null, totalScore, BigDecimal.ZERO);
        }

        if (profile.scoringCriteria() != null && !profile.scoringCriteria().isEmpty()) {
            List<ScoringCriteriaItem> items = classificationPolicy.classifyAll(profile.scoringCriteria());
            return new ScoringCriteriaClassificationResult(null, items, null, BigDecimal.ZERO);
        }

        return ScoringCriteriaClassificationResult.empty();
    }

    /**
     * 评分标准分类结果。
     * structuredItems 和 textItems 互斥：前端根据哪个非空决定展示方式。
     */
    public record ScoringCriteriaClassificationResult(
            List<ScoringCriterion> structuredItems,
            List<ScoringCriteriaItem> textItems,
            BigDecimal totalScore,
            BigDecimal ignored
    ) {
        public static ScoringCriteriaClassificationResult empty() {
            return new ScoringCriteriaClassificationResult(null, null, null, BigDecimal.ZERO);
        }

        public boolean isStructured() {
            return structuredItems != null && !structuredItems.isEmpty();
        }
    }
}
