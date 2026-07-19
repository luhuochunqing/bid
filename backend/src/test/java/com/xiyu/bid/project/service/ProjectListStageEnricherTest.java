// Input: Mockito 桩 repository 批量返回
// Output: ProjectListStageEnricher 行为断言（批量加载 / reviewer 拼接 / bidStatus 回落 / 评标结果回填）
// Pos: backend test source
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * CO-591: 列表 4 列 enrichment 单测。
 *
 * 验证点：
 * 1. 空 projectIds 返回空上下文，不触发任何 DB 查询。
 * 2. 多审核人按 repository 返回顺序（createdAt 升序）用 / 拼接；userMap 缺失的 reviewer 被跳过。
 * 3. 无 ProjectResult 时 bidStatus 回落到 dto.bidResultStatus；有 ProjectResult 时以其 resultType 为准。
 * 4. 评标结果（subStage）不限定 stage=EVALUATING，只要有 ProjectEvaluation 即回填（CO-591 有意行为变更）。
 */
@ExtendWith(MockitoExtension.class)
class ProjectListStageEnricherTest {

    @Mock
    private BidDocumentReviewRepository bidDocumentReviewRepository;
    @Mock
    private BidReviewAssignmentRepository bidReviewAssignmentRepository;
    @Mock
    private ProjectEvaluationRepository projectEvaluationRepository;
    @Mock
    private ProjectResultRepository projectResultRepository;

    @InjectMocks
    private ProjectListStageEnricher enricher;

    private ProjectListStageEnricher.Context loadContext(List<Long> ids) {
        return enricher.loadContext(ids);
    }

    @Test
    void loadContext_emptyIds_returnsEmptyContextWithoutDbAccess() {
        var ctx = enricher.loadContext(List.of());
        assertThat(ctx).isNotNull();
        assertThat(enricher.collectReviewerIds(ctx)).isEmpty();
        verifyNoInteractions(
                bidDocumentReviewRepository,
                bidReviewAssignmentRepository,
                projectEvaluationRepository,
                projectResultRepository);
    }

    @Test
    void populate_multipleReviewers_joinedBySlashInRepositoryOrder() {
        BidDocumentReviewEntity review = BidDocumentReviewEntity.builder()
                .id(10L).projectId(1L).build();
        when(bidDocumentReviewRepository.findByProjectIdIn(List.of(1L)))
                .thenReturn(List.of(review));
        // repository 查询带 OrderByCreatedAtAsc，列表顺序即 createdAt 升序
        when(bidReviewAssignmentRepository.findByReviewIdInOrderByCreatedAtAsc(Set.of(10L)))
                .thenReturn(List.of(
                        BidReviewAssignmentEntity.builder().reviewId(10L).reviewerId(100L).build(),
                        BidReviewAssignmentEntity.builder().reviewId(10L).reviewerId(200L).build()));
        when(projectEvaluationRepository.findByProjectIdIn(List.of(1L))).thenReturn(List.of());
        when(projectResultRepository.findByProjectIdIn(List.of(1L))).thenReturn(List.of());

        var ctx = loadContext(List.of(1L));

        User u1 = new User(); u1.setId(100L); u1.setFullName("赵六");
        User u2 = new User(); u2.setId(200L); u2.setFullName("钱七");
        Map<Long, User> userMap = Map.of(100L, u1, 200L, u2);

        ProjectDTO dto = ProjectDTO.builder().id(1L).build();
        enricher.populate(dto, ctx, ProjectStage.DRAFTING, true, userMap);

        assertThat(dto.getBidReviewers()).isEqualTo("赵六/钱七");
        assertThat(enricher.collectReviewerIds(ctx)).containsExactlyInAnyOrder(100L, 200L);
    }

    @Test
    void populate_reviewerMissingFromUserMap_skipped() {
        BidDocumentReviewEntity review = BidDocumentReviewEntity.builder()
                .id(10L).projectId(1L).build();
        when(bidDocumentReviewRepository.findByProjectIdIn(List.of(1L)))
                .thenReturn(List.of(review));
        when(bidReviewAssignmentRepository.findByReviewIdInOrderByCreatedAtAsc(Set.of(10L)))
                .thenReturn(List.of(
                        BidReviewAssignmentEntity.builder().reviewId(10L).reviewerId(100L).build(),
                        BidReviewAssignmentEntity.builder().reviewId(10L).reviewerId(null).build(),
                        BidReviewAssignmentEntity.builder().reviewId(10L).reviewerId(999L).build()));
        when(projectEvaluationRepository.findByProjectIdIn(List.of(1L))).thenReturn(List.of());
        when(projectResultRepository.findByProjectIdIn(List.of(1L))).thenReturn(List.of());

        var ctx = loadContext(List.of(1L));

        User u1 = new User(); u1.setId(100L); u1.setFullName("赵六");
        ProjectDTO dto = ProjectDTO.builder().id(1L).build();
        enricher.populate(dto, ctx, ProjectStage.DRAFTING, true, Map.of(100L, u1));

        // null reviewerId 与 userMap 缺失的 999L 都被跳过，不输出空段
        assertThat(dto.getBidReviewers()).isEqualTo("赵六");
        assertThat(enricher.collectReviewerIds(ctx)).containsExactlyInAnyOrder(100L, 999L);
    }

    @Test
    void populate_allReviewersUnresolved_leavesBidReviewersNull() {
        BidDocumentReviewEntity review = BidDocumentReviewEntity.builder()
                .id(10L).projectId(1L).build();
        when(bidDocumentReviewRepository.findByProjectIdIn(List.of(1L)))
                .thenReturn(List.of(review));
        when(bidReviewAssignmentRepository.findByReviewIdInOrderByCreatedAtAsc(Set.of(10L)))
                .thenReturn(List.of(
                        BidReviewAssignmentEntity.builder().reviewId(10L).reviewerId(999L).build()));
        when(projectEvaluationRepository.findByProjectIdIn(List.of(1L))).thenReturn(List.of());
        when(projectResultRepository.findByProjectIdIn(List.of(1L))).thenReturn(List.of());

        var ctx = loadContext(List.of(1L));
        ProjectDTO dto = ProjectDTO.builder().id(1L).build();
        enricher.populate(dto, ctx, ProjectStage.DRAFTING, true, Map.of());

        assertThat(dto.getBidReviewers()).isNull();
    }

    @Test
    void populate_noProjectResult_bidStatusFallsBackToDtoBidResultStatus() {
        when(bidDocumentReviewRepository.findByProjectIdIn(List.of(1L))).thenReturn(List.of());
        when(projectEvaluationRepository.findByProjectIdIn(List.of(1L))).thenReturn(List.of());
        when(projectResultRepository.findByProjectIdIn(List.of(1L))).thenReturn(List.of());

        var ctx = loadContext(List.of(1L));
        ProjectDTO dto = ProjectDTO.builder().id(1L).bidResultStatus("ABANDONED").build();
        enricher.populate(dto, ctx, ProjectStage.CLOSED, true, Map.of());

        assertThat(dto.getBidStatus()).isEqualTo("ABANDONED");
        assertThat(dto.getServicePeriodYears()).isNull();
        assertThat(dto.getServicePeriodEndDate()).isNull();
    }

    @Test
    void populate_withProjectResult_setsContractFieldsAndOverridesBidStatus() {
        ProjectResult result = ProjectResult.builder()
                .projectId(1L)
                .resultType("WON")
                .servicePeriodYears(new BigDecimal("3.5"))
                .servicePeriodEndDate(LocalDate.of(2027, 6, 1))
                .build();
        when(bidDocumentReviewRepository.findByProjectIdIn(List.of(1L))).thenReturn(List.of());
        when(projectEvaluationRepository.findByProjectIdIn(List.of(1L))).thenReturn(List.of());
        when(projectResultRepository.findByProjectIdIn(List.of(1L))).thenReturn(List.of(result));

        var ctx = loadContext(List.of(1L));
        ProjectDTO dto = ProjectDTO.builder().id(1L).bidResultStatus("LOST").build();
        enricher.populate(dto, ctx, ProjectStage.CLOSED, true, Map.of());

        assertThat(dto.getServicePeriodYears()).isEqualByComparingTo(new BigDecimal("3.5"));
        assertThat(dto.getServicePeriodEndDate()).isEqualTo(LocalDate.of(2027, 6, 1));
        // resultType 优先于 dto.bidResultStatus
        assertThat(dto.getBidStatus()).isEqualTo("WON");
    }

    @Test
    void populate_evaluationOutsideEvaluatingStage_stillSetsSubStage() {
        ProjectEvaluation evaluation = ProjectEvaluation.builder()
                .id(5L).projectId(1L).subStage("RESULT_OUT").build();
        when(bidDocumentReviewRepository.findByProjectIdIn(List.of(1L))).thenReturn(List.of());
        when(projectEvaluationRepository.findByProjectIdIn(List.of(1L)))
                .thenReturn(List.of(evaluation));
        when(projectResultRepository.findByProjectIdIn(List.of(1L))).thenReturn(List.of());

        var ctx = loadContext(List.of(1L));
        ProjectDTO dto = ProjectDTO.builder().id(1L).build();
        // CO-591 行为变更：不再限定 stage=EVALUATING
        enricher.populate(dto, ctx, ProjectStage.DRAFTING, true, Map.of());

        assertThat(dto.getEvaluationSubStage()).isEqualTo("RESULT_OUT");
    }

    @Test
    void loadContext_duplicateProjectRows_mergesWithoutException() {
        // 防御 CO-027 类 Duplicate key：同一 projectId 多条记录时取第一条
        when(bidDocumentReviewRepository.findByProjectIdIn(List.of(1L))).thenReturn(List.of());
        when(projectEvaluationRepository.findByProjectIdIn(List.of(1L))).thenReturn(List.of(
                ProjectEvaluation.builder().id(5L).projectId(1L).subStage("IN_PROGRESS").build(),
                ProjectEvaluation.builder().id(6L).projectId(1L).subStage("RESULT_OUT").build()));
        when(projectResultRepository.findByProjectIdIn(List.of(1L))).thenReturn(List.of());

        var ctx = loadContext(List.of(1L));
        ProjectDTO dto = ProjectDTO.builder().id(1L).build();
        enricher.populate(dto, ctx, ProjectStage.EVALUATING, true, Map.of());

        assertThat(dto.getEvaluationSubStage()).isEqualTo("IN_PROGRESS");
    }
}
