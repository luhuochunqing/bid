// Input: alerts service and request DTOs
// Output: Alert History REST API endpoints with admin/manager-only history reads and actions
// Pos: Controller/控制器层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.alerts.controller;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.alerts.dto.AlertHistoryCreateRequest;
import com.xiyu.bid.alerts.dto.AlertHistoryResponse;
import com.xiyu.bid.alerts.dto.AlertStatisticsResponse;
import com.xiyu.bid.alerts.entity.AlertHistory;
import com.xiyu.bid.alerts.service.AlertHistoryCommandService;
import com.xiyu.bid.alerts.service.AlertHistoryQueryService;
import com.xiyu.bid.alerts.service.AlertHistoryService;
import com.xiyu.bid.config.PaginationConstants;
import com.xiyu.bid.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alerts/history")
@RequiredArgsConstructor
public class AlertHistoryController {

    private final AlertHistoryService alertHistoryService;
    private final AlertHistoryQueryService alertHistoryQueryService;
    private final AlertHistoryCommandService alertHistoryCommandService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Auditable(action = "CREATE", entityType = "AlertHistory", description = "Create alert history record")
    public ResponseEntity<ApiResponse<AlertHistoryResponse>> createAlertHistory(@Valid @RequestBody AlertHistoryCreateRequest request) {
        AlertHistory alertHistory = alertHistoryService.createAlertHistory(request);
        return ResponseEntity.ok(ApiResponse.success(
                "Alert history created successfully",
                alertHistoryQueryService.toResponse(alertHistory)
        ));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Page<AlertHistoryResponse>>> getAllAlertHistories(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) AlertHistory.AlertLevel level,
            @RequestParam(required = false) Long ruleId,
            @RequestParam(required = false) String relatedId) {

        if (size > PaginationConstants.MAX_PAGE_SIZE) {
            size = PaginationConstants.MAX_PAGE_SIZE;
        }

        Sort sort = sortDir.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<AlertHistoryResponse> alertHistories = alertHistoryQueryService.getAllAlertHistories(pageable, status, level, ruleId, relatedId);
        return ResponseEntity.ok(ApiResponse.success(alertHistories));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<AlertHistoryResponse>> getAlertHistoryById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(alertHistoryQueryService.getAlertHistoryResponseById(id)));
    }

    @GetMapping("/unresolved")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Page<AlertHistoryResponse>>> getUnresolvedAlertHistories(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, PaginationConstants.MAX_PAGE_SIZE), Sort.by("createdAt").descending());
        return ResponseEntity.ok(ApiResponse.success(alertHistoryQueryService.getUnresolvedAlertHistories(pageable)));
    }

    @PatchMapping("/{id}/acknowledge")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<AlertHistoryResponse>> acknowledgeAlertHistory(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Alert history acknowledged successfully", alertHistoryCommandService.acknowledgeAlertHistory(id)));
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Auditable(action = "RESOLVE", entityType = "AlertHistory", description = "Resolve alert history")
    public ResponseEntity<ApiResponse<AlertHistoryResponse>> resolveAlertHistory(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Alert history resolved successfully", alertHistoryCommandService.resolveAlertHistory(id)));
    }

    @GetMapping("/statistics")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<AlertStatisticsResponse>> getAlertStatistics() {
        return ResponseEntity.ok(ApiResponse.success(alertHistoryQueryService.getAlertStatistics()));
    }
}
