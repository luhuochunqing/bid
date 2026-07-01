// Input: HTTP GET /api/projects/{id}/stage
// Output: ApiResponse<StageViewDto> — 当前阶段 + 允许的下一阶段候选 + 当前用户可访问阶段
// Pos: project/controller/ - WS-G 编排只读入口
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.project.core.ProjectStage;
import com.xiyu.bid.project.service.BidReviewAppService;
import com.xiyu.bid.project.service.ProjectStageService;
import com.xiyu.bid.service.AuthService;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/stage")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ProjectStageController {

    private final ProjectStageService service;
    private final BidReviewAppService bidReviewAppService;
    private final ProjectAccessScopeService projectAccessScopeService;
    private final AuthService authService;

    @GetMapping
    public ResponseEntity<ApiResponse<StageViewDto>> get(@PathVariable Long projectId,
                                                          @AuthenticationPrincipal UserDetails userDetails) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        ProjectStage actual = service.currentStage(projectId);
        // CO-443: 已提交结项申请但审批未通过时，阶段尚未实际推进到 CLOSED，
        // 但前端进度导航栏应显示 CLOSED 为「进行中」而非「待进入」。
        ProjectStage current = (actual != ProjectStage.CLOSED && service.hasClosureSubmission(projectId))
                ? ProjectStage.CLOSED
                : actual;
        List<ProjectStage> next = current.isTerminal()
                ? List.of()
                : service.allowedNext(projectId);
        List<String> completed = Arrays.stream(ProjectStage.values())
                .filter(s -> s.ordinal() < current.ordinal())
                .map(Enum::name).toList();
        List<String> accessible = new ArrayList<>(completed);
        accessible.add(current.name());
        String defaultOpenStage = current.name();
        if (isAssignedReviewingUser(projectId, userDetails)) {
            accessible.add(ProjectStage.DRAFTING.name());
            defaultOpenStage = ProjectStage.DRAFTING.name();
        }
        return ResponseEntity.ok(ApiResponse.success("ok",
                StageViewDto.builder()
                        .projectId(projectId)
                        .currentStage(current.name())
                        .completedStages(completed)
                        .allowedNextStages(next.stream().map(Enum::name).toList())
                        .accessibleStages(accessible.stream().distinct().toList())
                        .defaultOpenStage(defaultOpenStage)
                        // CO-443: terminal 基于实际 stage 判断，只有审批通过后 stage=CLOSED �为终态
                        .terminal(actual.isTerminal())
                        .build()));
    }

    private boolean isAssignedReviewingUser(Long projectId, UserDetails userDetails) {
        Long userId = currentUserId(userDetails);
        BidReviewAppService.ReviewState reviewState = bidReviewAppService.getReviewState(projectId);
        return "REVIEWING".equals(reviewState.status())
                && userId != null
                && userId.equals(reviewState.reviewerId());
    }

    private Long currentUserId(UserDetails userDetails) {
        if (userDetails == null || userDetails.getUsername() == null || userDetails.getUsername().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "无法识别当前用户");
        }
        return authService.resolveUserIdByUsername(userDetails.getUsername().trim());
    }

    @Data
    @Builder
    public static class StageViewDto {
        private Long projectId;
        private String currentStage;
        private List<String> completedStages;
        private List<String> allowedNextStages;
        private List<String> accessibleStages;
        private String defaultOpenStage;
        private boolean terminal;
    }
}
