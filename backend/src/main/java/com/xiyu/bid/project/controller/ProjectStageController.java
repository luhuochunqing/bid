// Input: HTTP GET /api/projects/{id}/stage
// Output: ApiResponse<StageViewDto> — 当前阶段 + 允许的下一阶段候选
// Pos: project/controller/ - WS-G 编排只读入口
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.project.core.ProjectStage;
import com.xiyu.bid.project.service.ProjectStageService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/stage")
@RequiredArgsConstructor
public class ProjectStageController {

    private final ProjectStageService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")
    public ResponseEntity<ApiResponse<StageViewDto>> get(@PathVariable Long projectId) {
        ProjectStage current = service.currentStage(projectId);
        List<ProjectStage> next = service.allowedNext(projectId);
        List<String> completed = java.util.Arrays.stream(ProjectStage.values())
                .filter(s -> s.ordinal() < current.ordinal())
                .map(Enum::name).toList();
        return ResponseEntity.ok(ApiResponse.success("ok",
                StageViewDto.builder()
                        .projectId(projectId)
                        .currentStage(current.name())
                        .completedStages(completed)
                        .allowedNextStages(next.stream().map(Enum::name).toList())
                        .terminal(current.isTerminal())
                        .build()));
    }

    @Data
    @Builder
    public static class StageViewDto {
        private Long projectId;
        private String currentStage;
        private List<String> completedStages;
        private List<String> allowedNextStages;
        private boolean terminal;
    }
}
