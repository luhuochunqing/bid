// Input: HTTP 请求 (preview/submit/approve/reject closure)
// Output: ApiResponse<ClosurePreviewDTO | ClosureDTO>
// Pos: project/controller/ - WS-F 结项 + 保证金强校验 + 审核流程（蓝图 §3.3.1.6）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.project.dto.ClosureDTO;
import com.xiyu.bid.project.dto.ClosurePreviewDTO;
import com.xiyu.bid.project.dto.ClosureReviewRequest;
import com.xiyu.bid.project.dto.ClosureSubmitRequest;
import com.xiyu.bid.project.service.ProjectClosureService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/projects/{projectId}/closure")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("isAuthenticated()")
public class ProjectClosureController {

    private final ProjectClosureService service;
    private final AuthService authService;

    /** 结项预览：所有人可查看。 */
    @GetMapping("/preview")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<ClosurePreviewDTO>> preview(@PathVariable Long projectId) {
        ClosurePreviewDTO dto = service.preview(projectId);
        return ResponseEntity.ok(ApiResponse.success("ok", dto));
    }

    /** 提交结项申请：管理员/组长/项目负责人/投标负责人/投标辅助（任务执行人不可提交）。 */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'BID_PROJECTLEADER')")
    public ResponseEntity<ApiResponse<ClosureDTO>> submit(
            @PathVariable Long projectId,
            @Valid @RequestBody ClosureSubmitRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = currentUserId(userDetails);
        // 422：请求校验
        if (req.getDepositReturnStatus() != null) {
            validateDepositFields(req);
        }
        ClosureDTO dto = service.submitClosure(projectId, req, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("结项申请已提交，等待审核", dto));
    }

    /** 审核通过：系统管理员/投标管理员/投标主管/投标组长/投标辅助（项目负责人不可审核）。 */
    @PostMapping("/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'BIDADMIN', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_TEAM')")
    public ResponseEntity<ApiResponse<ClosureDTO>> approve(
            @PathVariable Long projectId,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = currentUserId(userDetails);
        ClosureDTO dto = service.approveClosure(projectId, userId);
        return ResponseEntity.ok(ApiResponse.success("项目结项审核通过", dto));
    }

    /** 审核驳回：管理员/组长/投标负责人/投标辅助。 */
    @PostMapping("/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_TEAM')")
    public ResponseEntity<ApiResponse<ClosureDTO>> reject(
            @PathVariable Long projectId,
            @Valid @RequestBody ClosureReviewRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = currentUserId(userDetails);
        String reason = req != null ? req.getComment() : null;
        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "驳回时必须填写原因");
        }
        ClosureDTO dto = service.rejectClosure(projectId, reason, userId);
        return ResponseEntity.ok(ApiResponse.success("项目结项申请已驳回", dto));
    }

    /** 二次招标：管理员/组长/项目负责人/投标负责人/投标辅助。 */
    @PostMapping("/rebid")
    @PreAuthorize("hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_PROJECTLEADER', 'BID_TEAM')")
    public ResponseEntity<ApiResponse<Object>> rebid(
            @PathVariable Long projectId,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = currentUserId(userDetails);
        Long newProjectId = service.rebidProject(projectId, userId);
        return ResponseEntity.ok(ApiResponse.success("二次招标项目已创建", java.util.Map.of("projectId", newProjectId)));
    }

    private void validateDepositFields(ClosureSubmitRequest req) {
        String status = req.getDepositReturnStatus();
        if ("FULLY_RETURNED".equals(status)) {
            if (req.getDepositReturnDate() == null || req.getDepositReturnEvidenceId() == null) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "全部退回时，必须提供退回日期与退回凭证");
            }
        } else if ("TRANSFERRED_TO_FEE".equals(status)) {
            if (req.getTransferAmount() == null || req.getTransferAmount().compareTo(BigDecimal.ZERO) <= 0
                    || req.getDepositReturnEvidenceId() == null) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "转平台服务费时，必须提供转服务费金额与证明文件");
            }
        } else if ("PARTIAL_RETURN_PARTIAL_TRANSFER".equals(status)) {
            if (req.getReturnedAmount() == null || req.getReturnedAmount().compareTo(BigDecimal.ZERO) <= 0
                    || req.getTransferAmount() == null || req.getTransferAmount().compareTo(BigDecimal.ZERO) <= 0
                    || req.getDepositReturnEvidenceId() == null) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "部分退回部分转服务费时，必须提供退回金额、转服务费金额与证明文件");
            }
        }
    }

    private Long currentUserId(UserDetails userDetails) {
        if (userDetails == null || userDetails.getUsername() == null || userDetails.getUsername().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "无法识别当前用户");
        }
        return authService.resolveUserIdByUsername(userDetails.getUsername().trim());
    }
}