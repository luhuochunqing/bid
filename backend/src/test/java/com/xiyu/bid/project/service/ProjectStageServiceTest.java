// Input: Mockito 桩 ProjectRepository
// Output: 6 阶段线性推进 / 跨级 409 / CLOSED 终态 / null 入参 等行为断言
// Pos: backend test source
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.service;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.project.core.ProjectStage;
import com.xiyu.bid.project.core.ProjectStageTransitionPolicy;
import com.xiyu.bid.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectStageServiceTest {

    private ProjectRepository projectRepo;
    private ProjectStageService service;

    private static final Long PID = 1L;
    private static final ProjectStageTransitionPolicy.GateInputs GATE =
            ProjectStageTransitionPolicy.GateInputs.EMPTY;

    @BeforeEach
    void setup() {
        projectRepo = mock(ProjectRepository.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        service = new ProjectStageService(projectRepo, eventPublisher);
        when(projectRepo.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Project mockProjectAtStage(ProjectStage stage) {
        Project p = new Project();
        p.setId(PID);
        p.setStage(stage.name());
        when(projectRepo.findById(PID)).thenReturn(Optional.of(p));
        return p;
    }

    // ---------- happy 6-stage walk ----------

    @Test
    void linearWalk_INITIATED_to_CLOSED_allOk() {
        // 模拟一次完整 6 阶段推进，repository 内态在每次 save 后保留新的 stage
        Project p = new Project();
        p.setId(PID);
        p.setStage(ProjectStage.INITIATED.name());
        when(projectRepo.findById(PID)).thenReturn(Optional.of(p));

        ProjectStage[] order = {
                ProjectStage.INITIATED,
                ProjectStage.DRAFTING,
                ProjectStage.EVALUATING,
                ProjectStage.RESULT_PENDING,
                ProjectStage.RETROSPECTIVE,
                ProjectStage.CLOSED
        };
        for (int i = 0; i < order.length - 1; i++) {
            ProjectStage out = service.requestTransition(PID, order[i + 1], GATE);
            assertEquals(order[i + 1], out, "step " + i + " expected next");
        }
    }

    // ---------- cross-jump 409 ----------

    @Test
    void crossJump_INITIATED_to_EVALUATING_throws409() {
        mockProjectAtStage(ProjectStage.INITIATED);
        var ex = assertThrows(ResponseStatusException.class,
                () -> service.requestTransition(PID, ProjectStage.EVALUATING, GATE));
        assertEquals(409, ex.getStatusCode().value());
        assertTrue(ex.getReason() != null && ex.getReason().contains("非法跳转"));
    }

    @Test
    void backward_DRAFTING_to_INITIATED_throws409() {
        mockProjectAtStage(ProjectStage.DRAFTING);
        var ex = assertThrows(ResponseStatusException.class,
                () -> service.requestTransition(PID, ProjectStage.INITIATED, GATE));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void selfTransition_throws409() {
        mockProjectAtStage(ProjectStage.DRAFTING);
        var ex = assertThrows(ResponseStatusException.class,
                () -> service.requestTransition(PID, ProjectStage.DRAFTING, GATE));
        assertEquals(409, ex.getStatusCode().value());
    }

    // ---------- CLOSED terminal ----------

    @Test
    void closedTerminal_anyOutgoing_throws409() {
        mockProjectAtStage(ProjectStage.CLOSED);
        for (ProjectStage to : ProjectStage.values()) {
            var ex = assertThrows(ResponseStatusException.class,
                    () -> service.requestTransition(PID, to, GATE),
                    "CLOSED→" + to + " should be denied");
            assertEquals(409, ex.getStatusCode().value());
        }
    }

    // ---------- audit + persistence ----------

    @Test
    void requestTransition_savesNewStageOnProject() {
        Project p = mockProjectAtStage(ProjectStage.INITIATED);
        service.requestTransition(PID, ProjectStage.DRAFTING, GATE);
        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepo).save(captor.capture());
        assertEquals(ProjectStage.DRAFTING.name(), captor.getValue().getStage());
        // 同一个 entity 引用被更新（@Auditable AOP 关心的是方法成功返回）
        assertEquals(ProjectStage.DRAFTING.name(), p.getStage());
    }

    @Test
    void requestTransition_syncsProjectStatusWithStage() {
        Project p = mockProjectAtStage(ProjectStage.INITIATED);
        p.setStatus(Project.Status.PENDING_INITIATION);

        service.requestTransition(PID, ProjectStage.DRAFTING, GATE);

        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepo).save(captor.capture());
        assertEquals(Project.Status.BIDDING, captor.getValue().getStatus());
        assertEquals(Project.Status.BIDDING, p.getStatus());
    }

    @Test
    void unknownProject_throws404() {
        when(projectRepo.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.requestTransition(99L, ProjectStage.DRAFTING, GATE));
    }

    // ---------- read-only views ----------

    @Test
    void currentStage_parsesNullAsInitiated() {
        Project p = new Project();
        p.setId(PID);
        p.setStage(null);
        when(projectRepo.findById(PID)).thenReturn(Optional.of(p));
        assertEquals(ProjectStage.INITIATED, service.currentStage(PID));
    }

    @Test
    void allowedNext_returnsCorrectCandidate() {
        mockProjectAtStage(ProjectStage.EVALUATING);
        assertEquals(java.util.List.of(ProjectStage.RESULT_PENDING), service.allowedNext(PID));
    }

    @Test
    void allowedNext_closed_isEmpty() {
        mockProjectAtStage(ProjectStage.CLOSED);
        assertTrue(service.allowedNext(PID).isEmpty());
    }

    @Test
    void currentStage_unknownEnumValue_throwsIllegalState() {
        Project p = new Project();
        p.setId(PID);
        p.setStage("BOGUS_STAGE");
        when(projectRepo.findById(PID)).thenReturn(Optional.of(p));
        assertThrows(IllegalStateException.class, () -> service.currentStage(PID));
    }

    // ---------- 档案 4.1.1.1.1: 阶段时间戳自动记录 ----------

    @Test
    void transition_to_EVALUATING_setsEvaluatingAt_whenNull() {
        Project p = mockProjectAtStage(ProjectStage.DRAFTING);
        p.setEvaluatingAt(null);
        service.requestTransition(PID, ProjectStage.EVALUATING, GATE);
        assertNotNull(p.getEvaluatingAt());
    }

    @Test
    void transition_to_CLOSED_setsClosedAt_whenNull() {
        Project p = mockProjectAtStage(ProjectStage.RESULT_PENDING);
        p.setClosedAt(null);
        service.requestTransition(PID, ProjectStage.CLOSED, GATE);
        assertNotNull(p.getClosedAt());
    }

    @Test
    void reenter_EVALUATING_doesNOT_overwrite_existingEvaluatingAt() {
        Project p = mockProjectAtStage(ProjectStage.DRAFTING);
        LocalDateTime original = LocalDateTime.of(2020, 1, 1, 0, 0);
        p.setEvaluatingAt(original);
        service.requestTransition(PID, ProjectStage.EVALUATING, GATE);
        assertEquals(original, p.getEvaluatingAt(), "first-write-wins: existing timestamp must not be overwritten");
    }

    @Test
    void reenter_CLOSED_doesNOT_overwrite_existingClosedAt() {
        Project p = mockProjectAtStage(ProjectStage.RESULT_PENDING);
        LocalDateTime original = LocalDateTime.of(2020, 1, 1, 0, 0);
        p.setClosedAt(original);
        service.requestTransition(PID, ProjectStage.CLOSED, GATE);
        assertEquals(original, p.getClosedAt(), "first-write-wins: existing timestamp must not be overwritten");
    }

    @Test
    void failed_transition_doesNOT_setEvaluatingAt() {
        // INITIATED → EVALUATING is an illegal cross-jump (returns 409)
        Project p = mockProjectAtStage(ProjectStage.INITIATED);
        p.setEvaluatingAt(null);
        try {
            service.requestTransition(PID, ProjectStage.EVALUATING, GATE);
        } catch (ResponseStatusException ignored) {
            // expected
        }
        assertNull(p.getEvaluatingAt(), "failed transition must not set evaluatingAt");
    }

    @Test
    void failed_transition_doesNOT_setClosedAt() {
        Project p = mockProjectAtStage(ProjectStage.INITIATED);
        p.setClosedAt(null);
        try {
            service.requestTransition(PID, ProjectStage.CLOSED, GATE);
        } catch (ResponseStatusException ignored) {
            // expected
        }
        assertNull(p.getClosedAt(), "failed transition must not set closedAt");
    }
}
