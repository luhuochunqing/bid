// Input: projectIds / ProjectDTO / stage / submitted / userMap
// Output: 批量加载并回填列表 4 列（标书审核人 / 评标结果 / 服务周期 / bidStatus）
// Pos: project/service/ - 列表 enrichment 辅助组件
package com.xiyu.bid.project.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.project.core.ProjectStage;
import com.xiyu.bid.project.dto.ProjectDTO;
import com.xiyu.bid.project.entity.BidDocumentReviewEntity;
import com.xiyu.bid.project.entity.BidReviewAssignmentEntity;
import com.xiyu.bid.project.entity.ProjectEvaluation;
import com.xiyu.bid.project.entity.ProjectResult;
import com.xiyu.bid.project.repository.BidDocumentReviewRepository;
import com.xiyu.bid.project.repository.BidReviewAssignmentRepository;
import com.xiyu.bid.project.repository.ProjectEvaluationRepository;
import com.xiyu.bid.project.repository.ProjectResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * CO-591: 投标项目列表 stage 相关 enrichment 辅助组件。
 * <p>从 {@link ProjectQueryService} 抽离以守住 300 行硬上限。负责：
 * <ul>
 *   <li>批量加载 bid_document_review / bid_review_assignment / project_evaluation / project_result</li>
 *   <li>回填"标书审核人"（多人审核按 createdAt 升序，用 {@code /} 分隔）</li>
 *   <li>回填"评标结果"（{@code ProjectEvaluation.subStage}，不再限定 stage=EVALUATING）</li>
 *   <li>回填"项目服务周期（年）"与"服务周期截止时间"（来自 ProjectResult）</li>
 *   <li>计算 {@code bidStatus}（依赖 ProjectResult.resultType + stage + submitted）</li>
 * </ul>
 * <p>设计为有状态上下文模式：调用方先 {@link #loadContext(Collection)} 一次性批量加载，
 * 再 {@link #collectReviewerIds(Context)} 把审核人用户 ID 合并到统一 userMap，
 * 最后在循环里 {@link #populate} 回填字段，避免 N+1。
 */
@Component
@RequiredArgsConstructor
final class ProjectListStageEnricher {

    private final BidDocumentReviewRepository bidDocumentReviewRepository;
    private final BidReviewAssignmentRepository bidReviewAssignmentRepository;
    private final ProjectEvaluationRepository projectEvaluationRepository;
    private final ProjectResultRepository projectResultRepository;

    /** 一次性批量加载 stage 相关实体，避免循环内 N+1。返回不透明上下文。 */
    Context loadContext(final Collection<Long> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) {
            return Context.empty();
        }
        Map<Long, BidDocumentReviewEntity> bidReviewMap = bidDocumentReviewRepository
                .findByProjectIdIn(projectIds).stream()
                .collect(Collectors.toMap(
                        BidDocumentReviewEntity::getProjectId,
                        Function.identity(),
                        (a, b) -> a));
        Set<Long> reviewIds = bidReviewMap.values().stream()
                .map(BidDocumentReviewEntity::getId)
                .collect(Collectors.toSet());
        Map<Long, List<BidReviewAssignmentEntity>> reviewAssignmentsByReviewId = reviewIds.isEmpty()
                ? Collections.emptyMap()
                : bidReviewAssignmentRepository
                        .findByReviewIdInOrderByCreatedAtAsc(reviewIds).stream()
                        .collect(Collectors.groupingBy(
                                BidReviewAssignmentEntity::getReviewId));
        Map<Long, ProjectEvaluation> evaluationMap = projectEvaluationRepository
                .findByProjectIdIn(projectIds).stream()
                .collect(Collectors.toMap(
                        ProjectEvaluation::getProjectId,
                        Function.identity(),
                        (a, b) -> a));
        Map<Long, ProjectResult> projectResultMap = projectResultRepository
                .findByProjectIdIn(projectIds).stream()
                .collect(Collectors.toMap(
                        ProjectResult::getProjectId,
                        Function.identity(),
                        (a, b) -> a));
        return new Context(
                bidReviewMap,
                reviewAssignmentsByReviewId,
                evaluationMap,
                projectResultMap);
    }

    /** 收集 reviewer 用户 ID，由调用方合并到统一 userMap 中减 DB round-trip。 */
    Set<Long> collectReviewerIds(final Context ctx) {
        return ctx.reviewAssignmentsByReviewId.values().stream()
                .flatMap(List::stream)
                .map(BidReviewAssignmentEntity::getReviewerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * 回填列表 4 列 + bidStatus：
     * <ol>
     *   <li>bidStatus（依赖 ProjectResult.resultType + stage + submitted）</li>
     *   <li>项目服务周期（年）/ 服务周期截止时间（来自 ProjectResult）</li>
     *   <li>评标结果（ProjectEvaluation.subStage）</li>
     *   <li>标书审核人（多人 / 分隔，按 createdAt 升序）</li>
     * </ol>
     */
    void populate(final ProjectDTO dto,
                  final Context ctx,
                  final ProjectStage stage,
                  final boolean submitted,
                  final Map<Long, User> userMap) {
        ProjectResult projectResult = ctx.projectResultMap.get(dto.getId());
        String bidResult = projectResult != null
                ? projectResult.getResultType()
                : dto.getBidResultStatus();
        dto.setBidStatus(ProjectListEnrichmentSupport.computeBidStatus(
                stage, bidResult, submitted));
        if (projectResult != null) {
            dto.setServicePeriodYears(projectResult.getServicePeriodYears());
            dto.setServicePeriodEndDate(projectResult.getServicePeriodEndDate());
        }

        ProjectEvaluation evaluation = ctx.evaluationMap.get(dto.getId());
        if (evaluation != null) {
            dto.setEvaluationSubStage(evaluation.getSubStage());
        }

        BidDocumentReviewEntity bidReview = ctx.bidReviewMap.get(dto.getId());
        if (bidReview != null) {
            List<BidReviewAssignmentEntity> assignments = ctx.reviewAssignmentsByReviewId
                    .getOrDefault(bidReview.getId(), Collections.emptyList());
            String reviewers = assignments.stream()
                    .map(BidReviewAssignmentEntity::getReviewerId)
                    .filter(Objects::nonNull)
                    .map(rid -> {
                        User reviewer = userMap.get(rid);
                        return reviewer != null ? reviewer.getFullName() : null;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("/"));
            if (!reviewers.isEmpty()) {
                dto.setBidReviewers(reviewers);
            }
        }
    }

    /** 不透明上下文，仅供 {@link ProjectListStageEnricher} 与同包 Service 使用。 */
    static final class Context {
        private final Map<Long, BidDocumentReviewEntity> bidReviewMap;
        private final Map<Long, List<BidReviewAssignmentEntity>> reviewAssignmentsByReviewId;
        private final Map<Long, ProjectEvaluation> evaluationMap;
        private final Map<Long, ProjectResult> projectResultMap;

        private Context(final Map<Long, BidDocumentReviewEntity> bidReviewMap,
                        final Map<Long, List<BidReviewAssignmentEntity>> reviewAssignmentsByReviewId,
                        final Map<Long, ProjectEvaluation> evaluationMap,
                        final Map<Long, ProjectResult> projectResultMap) {
            this.bidReviewMap = bidReviewMap;
            this.reviewAssignmentsByReviewId = reviewAssignmentsByReviewId;
            this.evaluationMap = evaluationMap;
            this.projectResultMap = projectResultMap;
        }

        static Context empty() {
            return new Context(
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    Collections.emptyMap());
        }
    }
}
