// Input: AI service and request DTOs
// Output: Project AI REST API endpoints
// Pos: Controller/控制器层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.ai.controller;

import com.xiyu.bid.ai.dto.ProjectAiCardsDTO;
import com.xiyu.bid.ai.dto.ProjectScorePreviewDTO;
import com.xiyu.bid.ai.dto.ProjectScorePreviewRequestDTO;
import com.xiyu.bid.ai.service.AiDeepCapabilityService;
import com.xiyu.bid.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectAiController {

    private final AiDeepCapabilityService aiDeepCapabilityService;

    @PostMapping("/score-preview")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<ProjectScorePreviewDTO>> createScorePreview(
            @Valid @RequestBody ProjectScorePreviewRequestDTO request
    ) {
        ProjectScorePreviewDTO preview = aiDeepCapabilityService.createScorePreview(request, null);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Project score preview generated successfully", preview));
    }

    @GetMapping("/{projectId}/ai-cards")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<ProjectAiCardsDTO>> getProjectAiCards(@PathVariable Long projectId) {
        ProjectAiCardsDTO cards = aiDeepCapabilityService.getProjectAiCards(projectId);
        return ResponseEntity.ok(ApiResponse.success("Project AI cards retrieved successfully", cards));
    }
}
