// Input: HTTP 请求、路径参数、认证上下文和 DTO
// Output: 标准化 API 响应和用例入口
// Pos: Controller/接口适配层
// 维护声明: 仅维护标讯转派协议适配与参数校验；业务规则下沉到 service.
package com.xiyu.bid.tender.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.service.AuthService;
import com.xiyu.bid.tender.dto.TenderTransferRequest;
import com.xiyu.bid.tender.dto.TenderTransferResponse;
import com.xiyu.bid.tender.service.TenderTransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 标讯转派控制器。
 * <p>处理标讯转派相关操作。仅投标管理员/组长可操作。对应 FR-009 ~ FR-014。
 */
@RestController
@RequestMapping("/api/tenders")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("isAuthenticated()")
public class TenderTransferController {

    private final TenderTransferService tenderTransferService;
    private final AuthService authService;

    /**
     * 转派标讯。仅投标管理员/组长可操作。
     * FR-009 ~ FR-014
     */
    @PostMapping("/{id}/transfer")
    @PreAuthorize("hasAnyRole('ADMIN', 'BID_LEAD', 'BID_SENIOR')")
    public ResponseEntity<ApiResponse<TenderTransferResponse>> transferTender(
            @PathVariable Long id,
            @Valid @RequestBody TenderTransferRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("POST /api/tenders/{}/transfer - Transferring tender to newOwnerId: {}", id, request.getNewOwnerId());
        Long operatorId = resolveUserId(userDetails);
        TenderTransferResponse response = tenderTransferService.transfer(id, request.getNewOwnerId(), operatorId);
        return ResponseEntity.ok(ApiResponse.success("标讯转派成功", response));
    }

    private Long resolveUserId(UserDetails userDetails) {
        if (userDetails == null || userDetails.getUsername() == null || userDetails.getUsername().isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "无法识别当前用户");
        }
        return authService.resolveUserIdByUsername(userDetails.getUsername().trim());
    }
}
