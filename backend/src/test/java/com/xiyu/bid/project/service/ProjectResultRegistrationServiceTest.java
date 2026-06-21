// Input: Mockito 桩仓库
// Output: 注册结果服务行为断言（happy/422/409）
// Pos: backend test source
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.service;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.project.core.BidResultType;
import com.xiyu.bid.project.core.ProjectStage;
import com.xiyu.bid.project.core.ProjectStageTransitionPolicy;
import com.xiyu.bid.project.dto.ResultRegistrationRequest;
import com.xiyu.bid.project.entity.ProjectResult;
import com.xiyu.bid.project.notification.ProjectNotificationService;
import com.xiyu.bid.project.repository.ProjectResultCompetitorRepository;
import com.xiyu.bid.project.repository.ProjectResultRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectResultRegistrationServiceTest {

    private ProjectResultRepository repo;
    private ProjectResultCompetitorRepository competitorRepo;
    private ProjectRepository projectRepo;
    private TenderRepository tenderRepo;
    private UserRepository userRepo;
    private ProjectStageService stageService;
    private com.xiyu.bid.service.ProjectAccessScopeService accessScopeService;
    private ProjectNotificationService notificationService;
    private ApplicationEventPublisher eventPublisher;
    private ProjectResultRegistrationService service;

    @BeforeEach
    void setup() {
        repo = mock(ProjectResultRepository.class);
        projectRepo = mock(ProjectRepository.class);
        tenderRepo = mock(TenderRepository.class);
        userRepo = mock(UserRepository.class);
        stageService = mock(ProjectStageService.class);
        accessScopeService = mock(com.xiyu.bid.service.ProjectAccessScopeService.class);
        competitorRepo = mock(ProjectResultCompetitorRepository.class);
        notificationService = mock(ProjectNotificationService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new ProjectResultRegistrationService(repo, competitorRepo, projectRepo, tenderRepo, userRepo, stageService, accessScopeService, notificationService, eventPublisher);
        Project p = new Project();
        p.setId(1L);
        when(projectRepo.findById(1L)).thenReturn(Optional.of(p));
        lenient().when(stageService.currentStage(1L)).thenReturn(ProjectStage.RESULT_PENDING);
    }

    @Test
    void register_won_complete_persists() {
        when(repo.findByProjectId(1L)).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(inv -> {
            ProjectResult e = inv.getArgument(0);
            e.setId(99L);
            return e;
        });
        var req = ResultRegistrationRequest.builder()
                .resultType(BidResultType.WON)
                .awardAmount(new BigDecimal("88888"))
                .evidenceFileIds(List.of(11L, 12L))
                .summary("中标通知书已上传")
                .build();
        var dto = service.register(1L, req, 7L);
        assertEquals("WON", dto.getResultType());
        assertEquals(0, new BigDecimal("88888").compareTo(dto.getAwardAmount()));
        assertEquals(List.of(11L, 12L), dto.getEvidenceFileIds());
        assertNotNull(dto.getRegisteredAt());
        assertEquals(7L, dto.getRegisteredBy());
        verify(repo).save(any(ProjectResult.class));
    }

    @Test
    void register_atResultPending_advancesToRetrospective() {
        when(repo.findByProjectId(1L)).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(inv -> {
            ProjectResult e = inv.getArgument(0);
            e.setId(101L);
            return e;
        });
        when(stageService.currentStage(1L)).thenReturn(ProjectStage.RESULT_PENDING);
        var req = ResultRegistrationRequest.builder()
                .resultType(BidResultType.WON)
                .awardAmount(new BigDecimal("100"))
                .evidenceFileIds(List.of(1L))
                .build();
        service.register(1L, req, 7L);
        verify(stageService, times(1)).requestTransition(eq(1L),
                eq(ProjectStage.RETROSPECTIVE), any(ProjectStageTransitionPolicy.GateInputs.class),
                eq("WON"));
    }

    @Test
    void register_failed_advancesToClosed() {
        when(repo.findByProjectId(1L)).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(inv -> {
            ProjectResult e = inv.getArgument(0);
            e.setId(103L);
            return e;
        });
        when(stageService.currentStage(1L)).thenReturn(ProjectStage.RESULT_PENDING);
        var req = ResultRegistrationRequest.builder()
                .resultType(BidResultType.FAILED)
                .evidenceFileIds(List.of(1L))
                .summary("流标原因：不足三家")
                .build();
        service.register(1L, req, 7L);
        verify(stageService, times(1)).requestTransition(eq(1L),
                eq(ProjectStage.CLOSED), any(ProjectStageTransitionPolicy.GateInputs.class),
                eq("FAILED"));
    }

    @Test
    void register_notAtResultPending_doesNotAdvance() {
        when(repo.findByProjectId(1L)).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(inv -> {
            ProjectResult e = inv.getArgument(0);
            e.setId(102L);
            return e;
        });
        // 已被外部推进至 RETROSPECTIVE：不应再触发 stage transition
        when(stageService.currentStage(1L)).thenReturn(ProjectStage.RETROSPECTIVE);
        var req = ResultRegistrationRequest.builder()
                .resultType(BidResultType.WON)
                .awardAmount(new BigDecimal("100"))
                .evidenceFileIds(List.of(1L))
                .build();
        service.register(1L, req, 7L);
        verify(stageService, never()).requestTransition(any(), any(), any());
        verify(stageService, never()).requestTransition(any(), any(), any(), any());
    }

    @Test
    void register_atClosedStage_throws423() {
        when(stageService.currentStage(1L)).thenReturn(ProjectStage.CLOSED);
        var req = ResultRegistrationRequest.builder()
                .resultType(BidResultType.WON)
                .awardAmount(new BigDecimal("100"))
                .evidenceFileIds(List.of(1L))
                .build();
        var ex = assertThrows(ResponseStatusException.class,
                () -> service.register(1L, req, 7L));
        assertEquals(423, ex.getStatusCode().value());
        verify(repo, never()).save(any());
    }

    @Test
    void register_lost_missingEvidence_returns422() {
        when(repo.findByProjectId(1L)).thenReturn(Optional.empty());
        var req = ResultRegistrationRequest.builder()
                .resultType(BidResultType.LOST)
                .summary("竞争对手中标")
                .build();
        var ex = assertThrows(ResponseStatusException.class,
                () -> service.register(1L, req, 7L));
        assertEquals(422, ex.getStatusCode().value());
        assertTrue(ex.getReason() == null || ex.getReason().contains("evidenceFileIds"));
        verify(repo, never()).save(any());
    }

    @Test
    void register_failed_missingSummary_returns422() {
        when(repo.findByProjectId(1L)).thenReturn(Optional.empty());
        var req = ResultRegistrationRequest.builder()
                .resultType(BidResultType.FAILED)
                .evidenceFileIds(List.of(1L))
                .build();
        var ex = assertThrows(ResponseStatusException.class,
                () -> service.register(1L, req, 7L));
        assertEquals(422, ex.getStatusCode().value());
        verify(repo, never()).save(any());
    }

    @Test
    void register_alreadyRegistered_returns409() {
        ProjectResult existing = ProjectResult.builder()
                .id(50L).projectId(1L).resultType("WON").build();
        when(repo.findByProjectId(1L)).thenReturn(Optional.of(existing));
        var req = ResultRegistrationRequest.builder()
                .resultType(BidResultType.WON)
                .awardAmount(new BigDecimal("100"))
                .evidenceFileIds(List.of(1L))
                .build();
        var ex = assertThrows(ResponseStatusException.class,
                () -> service.register(1L, req, 7L));
        assertEquals(409, ex.getStatusCode().value());
        verify(repo, never()).save(any());
    }

    @Test
    void getByProject_present_returnsDto() {
        ProjectResult e = ProjectResult.builder()
                .id(33L).projectId(1L).resultType("ABANDONED")
                .summary("弃标").evidenceDocIds("5,6,7")
                .createdBy(99L).build();
        when(repo.findByProjectId(1L)).thenReturn(Optional.of(e));
        var dto = service.getByProject(1L).orElseThrow();
        assertEquals("ABANDONED", dto.getResultType());
        assertEquals(List.of(5L, 6L, 7L), dto.getEvidenceFileIds());
        assertEquals(99L, dto.getRegisteredBy());
    }

    @Test
    void getByProject_missing_emptyOptional() {
        when(repo.findByProjectId(1L)).thenReturn(Optional.empty());
        assertTrue(service.getByProject(1L).isEmpty());
    }

    @Test
    void getByProject_corruptedCsv_returnsEmptyEvidenceList() {
        ProjectResult e = ProjectResult.builder()
                .id(34L).projectId(1L).resultType("WON")
                .evidenceDocIds("1,foo,2") // 含非数字
                .createdBy(99L).build();
        when(repo.findByProjectId(1L)).thenReturn(Optional.of(e));
        var dto = service.getByProject(1L).orElseThrow();
        assertTrue(dto.getEvidenceFileIds().isEmpty(), "脏 CSV 应返回空 list 而不是抛异常");
    }
}
