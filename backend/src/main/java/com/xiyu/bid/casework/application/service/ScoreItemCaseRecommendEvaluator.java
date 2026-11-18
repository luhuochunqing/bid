// Input: candidate cases, match context, and metadata
// Output: evaluated, sorted, and paginated recommendation response
// Pos: backend/src/main/java/com/xiyu/bid/casework/application/service/ - Evaluator Collaborator
// 维护声明: 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.casework.application.service;

import com.xiyu.bid.casework.application.util.SummaryTruncator;
import com.xiyu.bid.casework.domain.model.KnowledgeCaseReadModel;
import com.xiyu.bid.casework.domain.policy.ScoreItemCaseMatchPolicy;
import com.xiyu.bid.casework.dto.ScoreItemCaseRecommendResponse;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 评分项案例推荐结果评估与分页装配器（FP-Java 协作类）。
 *
 * <p>负责候选案例评分计算、有效项过滤、置顶排序、分页截断及 DTO 组装。</p>
 */
final class ScoreItemCaseRecommendEvaluator {

    private static final DateTimeFormatter CREATED_AT_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final ScoreItemCaseMatchPolicy matchPolicy;

    ScoreItemCaseRecommendEvaluator() {
        this(new ScoreItemCaseMatchPolicy());
    }

    ScoreItemCaseRecommendEvaluator(ScoreItemCaseMatchPolicy matchPolicy) {
        this.matchPolicy = matchPolicy;
    }

    ScoreItemCaseRecommendResponse evaluateAndAssemble(
            List<KnowledgeCaseReadModel> candidateCases,
            ScoreItemCaseMatchPolicy.ScoreItemContext context,
            Map<Long, ScoreItemCaseSourceMetaLoader.SourceMeta> sourceMetaByProjectId,
            Integer pageParam,
            Integer sizeParam) {
        return evaluateAndAssemble(candidateCases, context, sourceMetaByProjectId, pageParam, sizeParam, List.of());
    }

    ScoreItemCaseRecommendResponse evaluateAndAssemble(
            List<KnowledgeCaseReadModel> candidateCases,
            ScoreItemCaseMatchPolicy.ScoreItemContext context,
            Map<Long, ScoreItemCaseSourceMetaLoader.SourceMeta> sourceMetaByProjectId,
            Integer pageParam,
            Integer sizeParam,
            List<ScoreItemCaseRecommendResponse.CertificateItem> matchedCertificates) {

        List<ScoreItemCaseMatchPolicy.MatchResult> results = matchPolicy.evaluateAll(candidateCases, context);
        List<ScoreItemCaseRecommendResponse.Item> evaluatedItems = new ArrayList<>(candidateCases.size());

        for (int i = 0; i < candidateCases.size(); i++) {
            KnowledgeCaseReadModel candidate = candidateCases.get(i);
            ScoreItemCaseMatchPolicy.MatchResult result = results.get(i);
            ScoreItemCaseRecommendResponse.Item item = toResponseItem(
                    candidate, result, sourceMetaByProjectId.get(candidate.getSourceProjectId()));
            if (item.getMatchScore() > 0) {
                evaluatedItems.add(item);
            }
        }

        evaluatedItems = evaluatedItems.stream()
                .sorted(Comparator.comparing(ScoreItemCaseRecommendResponse.Item::getIsPinned, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ScoreItemCaseRecommendResponse.Item::getMatchScore, Comparator.reverseOrder())
                        .thenComparing(ScoreItemCaseRecommendResponse.Item::getReuseCount, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        int page = pageParam != null && pageParam >= 0 ? pageParam : 0;
        int size = sizeParam != null && sizeParam > 0 ? sizeParam : 10;
        int fromIndex = Math.min(page * size, evaluatedItems.size());
        int toIndex = Math.min(fromIndex + size, evaluatedItems.size());

        List<ScoreItemCaseRecommendResponse.Item> pageItems = evaluatedItems.subList(fromIndex, toIndex);

        return ScoreItemCaseRecommendResponse.builder()
                .total(evaluatedItems.size())
                .items(pageItems)
                .matchedCertificates(matchedCertificates != null ? matchedCertificates : List.of())
                .build();
    }

    private ScoreItemCaseRecommendResponse.Item toResponseItem(
            KnowledgeCaseReadModel candidate,
            ScoreItemCaseMatchPolicy.MatchResult result,
            ScoreItemCaseSourceMetaLoader.SourceMeta sourceMeta) {
        String summary = SummaryTruncator.truncate(candidate.getResponseText(), 200);

        return ScoreItemCaseRecommendResponse.Item.builder()
                .id(candidate.getId())
                .sourceProjectId(candidate.getSourceProjectId())
                .sourceProjectName(candidate.getSourceProjectName())
                .scoringPointTitle(candidate.getScoringPointTitle())
                .scoringCategory(candidate.getScoringCategory())
                .matchScore(result.score())
                .matchLabel(result.matchLabel())
                .matchReasons(result.matchReasons())
                .matchedKeywords(result.matchedKeywords())
                .requirementRaw(candidate.getRequirementRaw())
                .responseText(candidate.getResponseText())
                .responseTextSummary(summary)
                .customerType(candidate.getCustomerType())
                .projectType(candidate.getProjectType())
                .bidResult(candidate.getBidResult())
                .reuseCount(candidate.getReuseCount() != null ? candidate.getReuseCount() : 0)
                .isPinned(candidate.getIsPinned() != null && candidate.getIsPinned())
                .createdAt(candidate.getCreatedAt() == null ? null : candidate.getCreatedAt().format(CREATED_AT_FORMAT))
                .bidEntity(sourceMeta == null ? null : sourceMeta.bidEntity())
                .projectStatus(sourceMeta == null ? null : sourceMeta.projectStatus())
                .subTopic(candidate.getSubTopic())
                .qualityScore(candidate.getQualityScore())
                .sourceSectionTitle(candidate.getSourceSectionTitle())
                .sourceSectionPath(candidate.getSourceSectionPath())
                .sourceDocumentName(candidate.getSourceDocumentName())
                .sourcePageNumber(candidate.getSourcePageNumber())
                .sourceSectionIndex(candidate.getSourceSectionIndex())
                .build();
    }
}
