// Input: Mockito 桩仓库
// Output: 提交行为断言（§2.6 复盘无需审核，提交即转 APPROVED）
// Pos: backend test source
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.service;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.project.entity.ProjectRetrospective;
import com.xiyu.bid.project.core.BidResultType;
import com.xiyu.bid.project.core.ProjectStage;
import com.xiyu.bid.project.dto.RetrospectiveSubmitRequest;
import com.xiyu.bid.project.repository.ProjectRetrospectiveRepository;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectRetrospectiveServiceTest {

    private ProjectRetrospectiveRepository repo;
    private ProjectRepository projectRepo;
    private ProjectStageService stageService;
    private UserRepository userRepository;
    private NotificationApplicationService notificationService;
    private ProjectRetrospectiveService service;

    @BeforeEach
    void setup() {
        repo = mock(ProjectRetrospectiveRepository.class);
        projectRepo = mock(ProjectRepository.class);
        stageService = mock(ProjectStageService.class);
        userRepository = mock(UserRepository.class);
        notificationService = mock(NotificationApplicationService.class);
        service = new ProjectRetrospectiveService(repo, projectRepo, stageService, userRepository, notificationService);
        Project p = new Project();
        p.setId(1L);
        when(projectRepo.findById(1L)).thenReturn(Optional.of(p));
        lenient().when(stageService.currentStage(1L)).thenReturn(ProjectStage.RESULT_PENDING);
    }

    @Test
    void submit_won_complete_persistsApproved() {
        when(repo.findByProjectId(1L)).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var req = RetrospectiveSubmitRequest.builder()
                .resultType(BidResultType.WON)
                .meetingTime("2025-06-01 10:00").meetingFormat("ONLINE").meetingParticipants("张三")
                .winFactors("优势").processHighlights("亮点").postWinImprovements("建议")
                .reportFileIds(java.util.List.of(1001L, 1002L))
                .build();
        var dto = service.submit(1L, req, 99L);
        // §2.6: 复盘无需审核，提交即转 APPROVED
        assertEquals("APPROVED", dto.getReviewStatus());
        assertEquals("WON", dto.getResultType());
        verify(repo).save(any(ProjectRetrospective.class));
    }

    @Test
    void submit_transitionsToClosedWhenAtRetrospectiveStage() {
        // §2.6: 复盘提交即推进 RETROSPECTIVE → CLOSED（无需审核）
        when(repo.findByProjectId(1L)).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // submit() 内 3 次 currentStage 读取：
        //   1) 锁校验（RESULT_PENDING 可写）
        //   2) afterSaveStage == RESULT_PENDING → 推进到 RETROSPECTIVE
        //   3) afterRetroTransition == RETROSPECTIVE → 推进到 CLOSED
        when(stageService.currentStage(1L))
                .thenReturn(ProjectStage.RESULT_PENDING)
                .thenReturn(ProjectStage.RESULT_PENDING)
                .thenReturn(ProjectStage.RETROSPECTIVE);
        var req = RetrospectiveSubmitRequest.builder()
                .resultType(BidResultType.WON)
                .meetingTime("2025-06-01 10:00").meetingFormat("ONLINE").meetingParticipants("张三")
                .winFactors("优势").processHighlights("亮点").postWinImprovements("建议")
                .reportFileIds(java.util.List.of(1001L, 1002L))
                .build();
        var dto = service.submit(1L, req, 99L);
        assertEquals("APPROVED", dto.getReviewStatus());
        // 两次推进：RESULT_PENDING→RETROSPECTIVE，RETROSPECTIVE→CLOSED
        verify(stageService).requestTransition(eq(1L), eq(ProjectStage.RETROSPECTIVE), any());
        verify(stageService).requestTransition(eq(1L), eq(ProjectStage.CLOSED), any());
    }

    @Test
    void submit_lost_missing_throws422() {
        var req = RetrospectiveSubmitRequest.builder()
                .resultType(BidResultType.LOST)
                .meetingTime("2025-06-01 10:00").meetingFormat("ONLINE").meetingParticipants("张三")
                .lossReasonFlags(java.util.List.of("NOT_IN_TARGET_LIST"))
                // 缺少 processProblems
                .postLossMeasures("措施").build();
        var ex = assertThrows(ResponseStatusException.class,
                () -> service.submit(1L, req, 99L));
        assertEquals(422, ex.getStatusCode().value());
        verify(repo, never()).save(any());
    }

    @Test
    void submit_atClosedStage_throws423() {
        when(stageService.currentStage(1L)).thenReturn(ProjectStage.CLOSED);
        var req = RetrospectiveSubmitRequest.builder()
                .resultType(BidResultType.WON)
                .meetingTime("2025-06-01 10:00").meetingFormat("ONLINE").meetingParticipants("张三")
                .winFactors("优势").processHighlights("亮点").postWinImprovements("建议")
                .build();
        var ex = assertThrows(ResponseStatusException.class,
                () -> service.submit(1L, req, 99L));
        assertEquals(423, ex.getStatusCode().value());
        verify(repo, never()).save(any());
    }
}
