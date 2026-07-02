// Input: HTTP 请求 (assignLeads / advance / submitReview / approve / reject / submitBid / get) + 当前用户
// Output: ApiResponse<ProjectDraftingViewDto>
// Pos: project/controller/
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.controller;

import com.xiyu.bid.annotation.LogOperation;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.project.dto.DraftingApprovalRequest;
import com.xiyu.bid.project.dto.DraftingRejectionRequest;
import com.xiyu.bid.project.dto.ProjectDraftingViewDto;
import com.xiyu.bid.project.dto.ProjectLeadAssignmentRequest;
import com.xiyu.bid.project.service.ProjectDraftingService;
import com.xiyu.bid.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/projects/{projectId}/drafting")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("isAuthenticated()")
public class ProjectDraftingController {

    private final ProjectDraftingService service;
    private final AuthService authService;

    /** 分配投标团队（管理员/组长）。 */
    @PatchMapping("/leads")
    @PreAuthorize("hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN')")
    public ResponseEntity<ApiResponse<ProjectDraftingViewDto>> assignLeads(
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectLeadAssignmentRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = currentUserId(userDetails);
        return ResponseEntity.ok(ApiResponse.success("leads assigned",
                service.assignLeads(projectId, req, userId)));
    }

    /** PRD §3.2.3 闸门：DRAFTING → EVALUATING 推进检查。 */
    @PostMapping("/advance")
    @PreAuthorize("hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_TEAM')")
    public ResponseEntity<ApiResponse<ProjectDraftingViewDto>> advance(
            @PathVariable Long projectId,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = currentUserId(userDetails);
        return ResponseEntity.ok(ApiResponse.success("advance ok",
                service.gateAdvanceToEvaluation(projectId, userId)));
    }

    /** 提交投标（审核通过 + 闸门通过后推进到评标中阶段）。 */
    @PostMapping("/submit-bid")
    @PreAuthorize("hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_PROJECTLEADER', 'BID_TEAM', 'SALES')")
    @LogOperation
    public ResponseEntity<ApiResponse<ProjectDraftingViewDto>> submitBid(
            @PathVariable Long projectId,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = currentUserId(userDetails);
        return ResponseEntity.ok(ApiResponse.success("bid submitted",
                service.submitBid(projectId, userId)));
    }

    /** 提交标书审核 */
    @PostMapping("/submit-review")
    @PreAuthorize("hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_PROJECTLEADER', 'BID_TEAM', 'SALES')")
    public ResponseEntity<ApiResponse<ProjectDraftingViewDto>> submitForReview(
            @PathVariable Long projectId,
            @RequestBody Map<String, Object> payload,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = currentUserId(userDetails);
        Long reviewerId = payload != null && payload.get("reviewerId") != null
                ? Long.valueOf(payload.get("reviewerId").toString()) : null;
        if (reviewerId == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "标书审核人不能为空");
        }
        return ResponseEntity.ok(ApiResponse.success("review submitted",
                service.submitForReview(projectId, reviewerId, userId)));
    }

    /** 审核通过（CO-315：鉴权下沉到项目访问权限 + BidReviewPolicy 校验审核人身份） */
    @PostMapping("/approve")
    public ResponseEntity<ApiResponse<ProjectDraftingViewDto>> approve(
            @PathVariable Long projectId,
            @Valid @RequestBody DraftingApprovalRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = currentUserId(userDetails);
        String comment = request.getComment() != null ? request.getComment() : "";
        return ResponseEntity.ok(ApiResponse.success("approved",
                service.approveBid(projectId, userId, comment)));
    }

    /** 驳回（CO-315：鉴权下沉到项目访问权限 + BidReviewPolicy 校验审核人身份） */
    @PostMapping("/reject")
    public ResponseEntity<ApiResponse<ProjectDraftingViewDto>> reject(
            @PathVariable Long projectId,
            @Valid @RequestBody DraftingRejectionRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = currentUserId(userDetails);
        return ResponseEntity.ok(ApiResponse.success("rejected",
                service.rejectBid(projectId, userId, request.getComment())));
    }

    /** 获取标书制作视图（CO-315：鉴权下沉到项目访问权限，审核人可见） */
    @GetMapping
    public ResponseEntity<ApiResponse<ProjectDraftingViewDto>> get(@PathVariable Long projectId) {
        return ResponseEntity.ok(ApiResponse.success("ok", service.get(projectId)));
    }

    private Long currentUserId(UserDetails userDetails) {
        if (userDetails == null || userDetails.getUsername() == null || userDetails.getUsername().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "无法识别当前用户");
        }
        return authService.resolveUserIdByUsername(userDetails.getUsername().trim());
    }
}
