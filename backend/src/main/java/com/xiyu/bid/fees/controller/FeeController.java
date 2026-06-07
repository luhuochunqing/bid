// Input: fees service and request DTOs
// Output: Fee REST API endpoints
// Pos: Controller/控制器层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.fees.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.fees.dto.FeeCreateRequest;
import com.xiyu.bid.fees.dto.FeeDTO;
import com.xiyu.bid.fees.dto.FeeStatisticsDTO;
import com.xiyu.bid.fees.dto.FeeUpdateRequest;
import com.xiyu.bid.fees.service.FeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.xiyu.bid.config.PaginationConstants;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 费用管理控制器
 * 处理费用相关的HTTP请求
 */
@RestController
@RequestMapping("/api/fees")
@RequiredArgsConstructor
@Slf4j
public class FeeController {

    private final FeeService feeService;

    /**
     * 创建费用
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<FeeDTO>> createFee(@Valid @RequestBody FeeCreateRequest request) {
        log.info("POST /api/fees - Creating fee for project: {}", request.getProjectId());
        FeeDTO createdFee = feeService.createFee(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Fee created successfully", createdFee));
    }

    /**
     * 获取所有费用（分页）
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Page<FeeDTO>>> getAllFees(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        log.info("GET /api/fees - Fetching all fees with pagination");

        // 安全限制：防止过大的分页请求
        if (size > PaginationConstants.MAX_PAGE_SIZE) {
            size = PaginationConstants.MAX_PAGE_SIZE;
        }

        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<FeeDTO> fees = feeService.getAllFees(pageable);
        return ResponseEntity.ok(ApiResponse.success("Successfully retrieved fees", fees));
    }

    /**
     * 根据ID获取费用
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<FeeDTO>> getFeeById(@PathVariable Long id) {
        log.info("GET /api/fees/{} - Fetching fee", id);
        FeeDTO fee = feeService.getFeeById(id);
        return ResponseEntity.ok(ApiResponse.success("Successfully retrieved fee", fee));
    }

    /**
     * 根据项目ID获取费用列表
     */
    @GetMapping("/project/{projectId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<FeeDTO>>> getFeesByProjectId(@PathVariable Long projectId) {
        log.info("GET /api/fees/project/{} - Fetching fees for project", projectId);
        List<FeeDTO> fees = feeService.getFeesByProjectId(projectId);
        return ResponseEntity.ok(ApiResponse.success("Successfully retrieved fees for project", fees));
    }

    /**
     * 根据状态获取费用列表
     */
    @GetMapping("/status/{status}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<FeeDTO>>> getFeesByStatus(@PathVariable FeeDTO.Status status) {
        log.info("GET /api/fees/status/{} - Fetching fees by status", status);
        List<FeeDTO> fees = feeService.getFeesByStatus(status);
        return ResponseEntity.ok(ApiResponse.success("Successfully retrieved fees by status", fees));
    }

    /**
     * 更新费用
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<FeeDTO>> updateFee(
            @PathVariable Long id,
            @Valid @RequestBody FeeUpdateRequest request) {

        log.info("PUT /api/fees/{} - Updating fee", id);
        FeeDTO updatedFee = feeService.updateFee(id, request);
        return ResponseEntity.ok(ApiResponse.success("Fee updated successfully", updatedFee));
    }

    /**
     * 删除费用
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteFee(@PathVariable Long id) {
        log.info("DELETE /api/fees/{} - Deleting fee", id);
        feeService.deleteFee(id);
        return ResponseEntity.ok(ApiResponse.success("Fee deleted successfully", null));
    }

    /**
     * 标记费用为已支付
     */
    @PostMapping("/{id}/pay")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<FeeDTO>> markAsPaid(
            @PathVariable Long id,
            @RequestParam String paidBy) {

        log.info("POST /api/fees/{}/pay - Marking fee as paid by: {}", id, paidBy);
        FeeDTO fee = feeService.markAsPaid(id, paidBy);
        return ResponseEntity.ok(ApiResponse.success("Fee marked as paid successfully", fee));
    }

    /**
     * 标记费用为已退还
     */
    @PostMapping("/{id}/return")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<FeeDTO>> markAsReturned(
            @PathVariable Long id,
            @RequestParam String returnTo) {

        log.info("POST /api/fees/{}/return - Marking fee as returned to: {}", id, returnTo);
        FeeDTO fee = feeService.markAsReturned(id, returnTo);
        return ResponseEntity.ok(ApiResponse.success("Fee marked as returned successfully", fee));
    }

    /**
     * 取消费用
     */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<FeeDTO>> cancelFee(@PathVariable Long id) {
        log.info("POST /api/fees/{}/cancel - Cancelling fee", id);
        FeeDTO fee = feeService.cancelFee(id);
        return ResponseEntity.ok(ApiResponse.success("Fee cancelled successfully", fee));
    }

    /**
     * 获取费用统计
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<FeeStatisticsDTO>> getStatistics(
            @RequestParam Long projectId) {

        log.info("GET /api/fees/statistics - Fetching statistics for project: {}", projectId);
        FeeStatisticsDTO statistics = feeService.getStatistics(projectId);
        return ResponseEntity.ok(ApiResponse.success("Successfully retrieved fee statistics", statistics));
    }
}
