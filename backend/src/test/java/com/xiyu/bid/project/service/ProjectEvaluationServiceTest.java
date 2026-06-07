// Input: Mockito 桩仓库 + ProjectStageService
// Output: 子状态切换 happy / 拒绝 / ANNOUNCED 自动推进 stage 行为断言
// Pos: backend test source
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.service;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.project.entity.ProjectEvaluation;
import com.xiyu.bid.project.core.EvaluationSubStage;
import com.xiyu.bid.project.core.ProjectStage;
import com.xiyu.bid.project.core.ProjectStageTransitionPolicy;
import com.xiyu.bid.project.dto.EvaluationEvidenceAttachRequest;
import com.xiyu.bid.project.dto.EvaluationFormUpdateRequest;
import com.xiyu.bid.project.dto.EvaluationSubStageUpdateRequest;
import com.xiyu.bid.project.dto.ProjectAbandonBidRequest;
import com.xiyu.bid.project.repository.ProjectEvaluationRepository;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectEvaluationServiceTest {

    private ProjectEvaluationRepository repo;
    private ProjectRepository projectRepo;
    private ProjectDocumentRepository docRepo;
    private ProjectStageService stageService;
    private ProjectEvaluationService service;

    @BeforeEach
    void setup() {
        repo = mock(ProjectEvaluationRepository.class);
        projectRepo = mock(ProjectRepository.class);
        docRepo = mock(ProjectDocumentRepository.class);
        stageService = mock(ProjectStageService.class);
        service = new ProjectEvaluationService(repo, projectRepo, docRepo, stageService);
        Project p = new Project();
        p.setId(1L);
        when(projectRepo.findById(1L)).thenReturn(Optional.of(p));
        lenient().when(docRepo.findByProjectIdAndFiltersOrderByCreatedAtDesc(any(), any(), any(), any()))
                .thenReturn(List.of());
        lenient().when(repo.save(any())).thenAnswer(inv -> {
            ProjectEvaluation e = inv.getArgument(0);
            if (e.getId() == null) e.setId(100L);
            return e;
        });
        lenient().when(stageService.currentStage(1L)).thenReturn(ProjectStage.EVALUATING);
    }

    @Test
    void transition_initToAwaitingBoard_happy() {
        ProjectEvaluation existing = ProjectEvaluation.builder()
                .id(10L).projectId(1L).subStage("IN_PROGRESS").build();
        when(repo.findByProjectId(1L)).thenReturn(Optional.of(existing));
        var req = EvaluationSubStageUpdateRequest.builder()
                .targetSubStage(EvaluationSubStage.AWAITING_BOARD).notes("评标情况说明").build();
        var dto = service.transitionSubStage(1L, req, 7L);
        assertEquals("AWAITING_BOARD", dto.getSubStage());
        // AWAITING_BOARD 不是 ANNOUNCED，advanceProjectStageToResultPending 不会被调用
        verify(stageService, never()).requestTransition(any(), any(), any());
    }

    @Test
    void transition_skip_in_progress_to_announced_allowed_by_blueprint() {
        // 蓝图 V1.1 §4.3: 子状态可自由切换，IN_PROGRESS → ANNOUNCED 是合法的跳转
        ProjectEvaluation existing = ProjectEvaluation.builder()
                .id(10L).projectId(1L).subStage("IN_PROGRESS").build();
        when(repo.findByProjectId(1L)).thenReturn(Optional.of(existing));
        var req = EvaluationSubStageUpdateRequest.builder()
                .targetSubStage(EvaluationSubStage.ANNOUNCED).notes("评标情况说明").build();
        var dto = service.transitionSubStage(1L, req, 7L);
        assertEquals("ANNOUNCED", dto.getSubStage());
    }

    @Test
    void transition_reverse_allowed_by_blueprint() {
        // 蓝图 V1.1 §4.3: 子状态可自由切换，AWAITING_BOARD → IN_PROGRESS 也允许
        ProjectEvaluation existing = ProjectEvaluation.builder()
                .id(10L).projectId(1L).subStage("AWAITING_BOARD").build();
        when(repo.findByProjectId(1L)).thenReturn(Optional.of(existing));
        var req = EvaluationSubStageUpdateRequest.builder()
                .targetSubStage(EvaluationSubStage.IN_PROGRESS).notes("评标情况说明").build();
        var dto = service.transitionSubStage(1L, req, 7L);
        assertEquals("IN_PROGRESS", dto.getSubStage());
    }

    @Test
    void transition_announced_autoAdvancesProjectStage() {
        ProjectEvaluation existing = ProjectEvaluation.builder()
                .id(10L).projectId(1L).subStage("AWAITING_BOARD").build();
        when(repo.findByProjectId(1L)).thenReturn(Optional.of(existing));
        var req = EvaluationSubStageUpdateRequest.builder()
                .targetSubStage(EvaluationSubStage.ANNOUNCED).notes("评标情况说明").build();
        var dto = service.transitionSubStage(1L, req, 7L);
        assertEquals("ANNOUNCED", dto.getSubStage());
        verify(stageService, times(1)).requestTransition(eq(1L),
                eq(ProjectStage.RESULT_PENDING), any(ProjectStageTransitionPolicy.GateInputs.class));
    }

    @Test
    void transition_announced_idempotent_whenStageAlreadyAdvanced() {
        // 已被外部推进至 RESULT_PENDING；本次 announce 不再触发 stage transition
        when(stageService.currentStage(1L)).thenReturn(ProjectStage.RESULT_PENDING);
        ProjectEvaluation existing = ProjectEvaluation.builder()
                .id(10L).projectId(1L).subStage("AWAITING_BOARD").build();
        when(repo.findByProjectId(1L)).thenReturn(Optional.of(existing));
        var req = EvaluationSubStageUpdateRequest.builder()
                .targetSubStage(EvaluationSubStage.ANNOUNCED).notes("评标情况说明").build();
        var dto = service.transitionSubStage(1L, req, 7L);
        assertEquals("ANNOUNCED", dto.getSubStage());
        verify(stageService, never()).requestTransition(any(), any(), any());
    }

    @Test
    void transition_initialEntityCreatedIfMissing() {
        when(repo.findByProjectId(1L)).thenReturn(Optional.empty());
        var req = EvaluationSubStageUpdateRequest.builder()
                .targetSubStage(EvaluationSubStage.AWAITING_BOARD).notes("评标情况说明").build();
        var dto = service.transitionSubStage(1L, req, 7L);
        assertEquals("AWAITING_BOARD", dto.getSubStage());
        verify(repo).save(any(ProjectEvaluation.class));
    }

    @Test
    void updateEvaluationForm_happy() {
        ProjectEvaluation existing = ProjectEvaluation.builder()
                .id(10L).projectId(1L).subStage("IN_PROGRESS").build();
        when(repo.findByProjectId(1L)).thenReturn(Optional.of(existing));
        var req = EvaluationFormUpdateRequest.builder()
                .background("测试背景")
                .competitors("竞争对手A、B")
                .contractPeriod("12个月")
                .shortlistedBidders(5)
                .platformFee(new BigDecimal("10000.00"))
                .previousBid("上一轮报价100万")
                .recommendation(true)
                .build();
        var dto = service.updateEvaluationForm(1L, req, 7L);
        assertEquals("测试背景", dto.getBackground());
        verify(repo).save(any());
    }

    @Test
    void attachEvidence_linksDocs() {
        ProjectEvaluation existing = ProjectEvaluation.builder()
                .id(10L).projectId(1L).subStage("IN_PROGRESS").build();
        when(repo.findByProjectId(1L)).thenReturn(Optional.of(existing));
        ProjectDocument d1 = ProjectDocument.builder().id(50L).projectId(1L).build();
        when(docRepo.findById(50L)).thenReturn(Optional.of(d1));
        var req = EvaluationEvidenceAttachRequest.builder()
                .fileIds(List.of(50L)).build();
        service.attachEvidence(1L, req, 7L);
        verify(docRepo).save(d1);
        assertEquals("EVALUATION", d1.getLinkedEntityType());
        assertEquals(10L, d1.getLinkedEntityId());
    }

    @Test
    void attachEvidence_docOfDifferentProject_rejected() {
        ProjectEvaluation existing = ProjectEvaluation.builder()
                .id(10L).projectId(1L).subStage("IN_PROGRESS").build();
        when(repo.findByProjectId(1L)).thenReturn(Optional.of(existing));
        ProjectDocument d1 = ProjectDocument.builder().id(50L).projectId(99L).build();
        when(docRepo.findById(50L)).thenReturn(Optional.of(d1));
        var req = EvaluationEvidenceAttachRequest.builder()
                .fileIds(List.of(50L)).build();
        var ex = assertThrows(ResponseStatusException.class,
                () -> service.attachEvidence(1L, req, 7L));
        assertEquals(422, ex.getStatusCode().value());
    }

    /** H6: 一票否决 — 任意 fileId 不属于 project，整批拒绝；先校验后写入，无副作用。 */
    @Test
    void attachEvidence_oneForeignFileId_rejectsEntireBatch_noSideEffects() {
        ProjectEvaluation existing = ProjectEvaluation.builder()
                .id(10L).projectId(1L).subStage("IN_PROGRESS").build();
        when(repo.findByProjectId(1L)).thenReturn(Optional.of(existing));
        ProjectDocument ok = ProjectDocument.builder().id(50L).projectId(1L).build();
        ProjectDocument foreign = ProjectDocument.builder().id(51L).projectId(99L).build();
        when(docRepo.findById(50L)).thenReturn(Optional.of(ok));
        when(docRepo.findById(51L)).thenReturn(Optional.of(foreign));
        var req = EvaluationEvidenceAttachRequest.builder()
                .fileIds(List.of(50L, 51L)).build();
        var ex = assertThrows(ResponseStatusException.class,
                () -> service.attachEvidence(1L, req, 7L));
        assertEquals(422, ex.getStatusCode().value());
        // 第一个合法 doc 不应被 save
        verify(docRepo, never()).save(any(ProjectDocument.class));
        // 评估实体也不应在校验失败后被新写
        verify(repo, never()).save(any(ProjectEvaluation.class));
    }

    @Test
    void transitionSubStage_atClosedStage_throws423() {
        when(stageService.currentStage(1L)).thenReturn(ProjectStage.CLOSED);
        var req = EvaluationSubStageUpdateRequest.builder()
                .targetSubStage(EvaluationSubStage.AWAITING_BOARD).notes("评标情况说明").build();
        var ex = assertThrows(ResponseStatusException.class,
                () -> service.transitionSubStage(1L, req, 7L));
        assertEquals(423, ex.getStatusCode().value());
        verify(repo, never()).save(any());
    }

    @Test
    void attachEvidence_atClosedStage_throws423() {
        when(stageService.currentStage(1L)).thenReturn(ProjectStage.CLOSED);
        var req = EvaluationEvidenceAttachRequest.builder()
                .fileIds(List.of(50L)).build();
        var ex = assertThrows(ResponseStatusException.class,
                () -> service.attachEvidence(1L, req, 7L));
        assertEquals(423, ex.getStatusCode().value());
        verify(repo, never()).save(any());
    }

    @Test
    void abandonBid_happy() {
        ProjectEvaluation existing = ProjectEvaluation.builder()
                .id(10L).projectId(1L).subStage("AWAITING_BOARD").build();
        when(repo.findByProjectId(1L)).thenReturn(Optional.of(existing));
        var req = ProjectAbandonBidRequest.builder().reason("竞争对手报价更低").build();
        var dto = service.abandonBid(1L, req, 7L);
        assertEquals("ANNOUNCED", dto.getSubStage());
        assertEquals("竞争对手报价更低", dto.getNotes());
        verify(stageService, times(1)).requestTransition(eq(1L),
                eq(ProjectStage.RESULT_PENDING), any(ProjectStageTransitionPolicy.GateInputs.class));
    }

    @Test
    void abandonBid_atClosedStage_throws423() {
        when(stageService.currentStage(1L)).thenReturn(ProjectStage.CLOSED);
        var req = ProjectAbandonBidRequest.builder().reason("原因").build();
        var ex = assertThrows(ResponseStatusException.class,
                () -> service.abandonBid(1L, req, 7L));
        assertEquals(423, ex.getStatusCode().value());
        verify(repo, never()).save(any());
    }

    @Test
    void abandonBid_idempotent_whenStageAlreadyAdvanced() {
        // 弃标成功推进 stage 后，再次调用应为幂等跳过
        when(stageService.currentStage(1L)).thenReturn(ProjectStage.RESULT_PENDING);
        ProjectEvaluation existing = ProjectEvaluation.builder()
                .id(10L).projectId(1L).subStage("ANNOUNCED").build();
        when(repo.findByProjectId(1L)).thenReturn(Optional.of(existing));
        var req = ProjectAbandonBidRequest.builder().reason("原因").build();
        var dto = service.abandonBid(1L, req, 7L);
        assertEquals("ANNOUNCED", dto.getSubStage());
        verify(stageService, never()).requestTransition(any(), any(), any());
    }
}
