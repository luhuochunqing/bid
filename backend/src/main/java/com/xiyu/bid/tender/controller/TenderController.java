package com.xiyu.bid.tender.controller;

import com.xiyu.bid.common.domain.PagedResult;
import com.xiyu.bid.demo.service.DemoDataProvider;
import com.xiyu.bid.demo.service.DemoFusionService;
import com.xiyu.bid.demo.service.DemoModeService;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.idempotency.Idempotent;
import com.xiyu.bid.tender.dto.TenderRequest;
import com.xiyu.bid.tender.dto.TenderDTO;
import com.xiyu.bid.tender.dto.TenderAbandonRequest;
import com.xiyu.bid.tender.dto.TenderBidResponse;
import com.xiyu.bid.tender.dto.TenderCrmLinkRequest;
import com.xiyu.bid.tender.service.*;
import com.xiyu.bid.util.InputSanitizer;
import com.xiyu.bid.annotation.DataScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import com.xiyu.bid.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.xiyu.bid.entity.AuditLog;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@Tag(name = "标讯管理", description = "标讯 CRUD、分页搜索与业务操作")
@RequestMapping("/api/tenders")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_PROJECTLEADER', 'BID_TEAM')")
public class TenderController {

    private final TenderQueryService tenderQueryService;
    private final TenderCommandService tenderCommandService;
    private final TenderSubmissionService tenderSubmissionService;
    private final TenderMapper tenderMapper;
    private final TenderImportService tenderImportService;
    private final DemoModeService demoModeService;
    private final DemoDataProvider demoDataProvider;
    private final DemoFusionService demoFusionService;
    private final TenderAuditService tenderAuditService;
    private final AuthService authService;
    private final TenderRequestSanitizer sanitizer = new TenderRequestSanitizer();

    @GetMapping
    @DataScope
    @Operation(summary = "标讯列表查询（分页）")
    public ResponseEntity<ApiResponse<PagedResult<TenderDTO>>> getAllTenders(@ModelAttribute TenderSearchCriteria criteria) {
        log.info("GET /api/tenders - criteria={}", criteria);
        sanitizeTenderSearchCriteria(criteria);
        Page<TenderDTO> page = tenderQueryService.searchTendersPaged(criteria, PageRequest.of(Math.max(criteria.getPage(), 0), criteria.getSize() > 0 ? criteria.getSize() : 20));
        PagedResult<TenderDTO> result = PagedResult.of(page.getContent(), page.getTotalElements(), page.getNumber(), page.getSize());
        if (demoModeService.isEnabled()) {
            List<TenderDTO> merged = demoFusionService.mergeByKey(result.content(), demoDataProvider.getDemoTenders(), TenderDTO::getId);
            result = PagedResult.of(merged, result.totalElements(), result.pageNumber(), result.pageSize());
        }
        return ResponseEntity.ok(ApiResponse.success("查询成功", result));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_PROJECTLEADER', 'BID_TEAM')")
    @Operation(summary = "标讯详情查询")
    public ResponseEntity<ApiResponse<TenderDTO>> getTenderById(@PathVariable Long id) {
        log.info("GET /api/tenders/{}", id);
        if (isDemoEntityId(id)) {
            return ResponseEntity.ok(ApiResponse.success("查询成功",
                    demoDataProvider.findDemoTenderById(id).orElseThrow(() -> new com.xiyu.bid.exception.ResourceNotFoundException("Tender", id.toString()))));
        }
        return ResponseEntity.ok(ApiResponse.success("查询成功", tenderQueryService.getTenderById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_PROJECTLEADER', 'BID_TEAM')")
    @Idempotent
    @Operation(summary = "创建标讯")
    public ResponseEntity<ApiResponse<TenderDTO>> createTender(@Valid @RequestBody TenderRequest req, @AuthenticationPrincipal UserDetails user) {
        log.info("POST /api/tenders - {}", req.getTitle());
        sanitizeTenderRequest(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("创建成功", tenderCommandService.createTender(tenderMapper.toDTO(req), resolveUserId(user))));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "修改标讯")
    public ResponseEntity<ApiResponse<TenderDTO>> updateTender(@PathVariable Long id, @Valid @RequestBody TenderRequest req, @AuthenticationPrincipal UserDetails user) {
        log.info("PUT /api/tenders/{}", id);
        rejectDemoMutation(id);
        sanitizeTenderRequest(req);
        return ResponseEntity.ok(ApiResponse.success("更新成功", tenderCommandService.updateTender(id, tenderMapper.toDTO(req), resolveUserId(user))));
    }

    @PatchMapping("/{id}/crm-opportunity")
    @PreAuthorize("hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_PROJECTLEADER', 'BID_TEAM')")
    @Operation(summary = "标讯关联CRM商机")
    public ResponseEntity<ApiResponse<TenderDTO>> linkCrmOpportunity(@PathVariable Long id, @Valid @RequestBody TenderCrmLinkRequest req, @AuthenticationPrincipal UserDetails user) {
        rejectDemoMutation(id);
        return ResponseEntity.ok(ApiResponse.success("CRM商机关联成功", tenderCommandService.linkCrmOpportunity(id, req.getCrmOpportunityId(), req.getCrmOpportunityName(), req.getEvaluationPayload(), resolveUserId(user))));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "删除标讯")
    public ResponseEntity<ApiResponse<Void>> deleteTender(@PathVariable Long id, @AuthenticationPrincipal UserDetails user) {
        rejectDemoMutation(id);
        tenderCommandService.deleteTender(id, resolveUserId(user));
        return ResponseEntity.ok(ApiResponse.success("删除成功", null));
    }

    @GetMapping("/{id}/audit-logs")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "标讯审计日志")
    public ResponseEntity<ApiResponse<List<AuditLog>>> getAuditLogs(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("查询成功", tenderAuditService.getAuditLogs(id)));
    }

    @PostMapping("/{id}/participate")
    @PreAuthorize("hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN')")
    @Operation(summary = "投标决策")
    public ResponseEntity<ApiResponse<TenderBidResponse>> participateBid(@PathVariable Long id, @AuthenticationPrincipal UserDetails user) {
        rejectDemoMutation(id);
        TenderBidResponse resp = tenderSubmissionService.participateBid(id, resolveUserId(user));
        return ResponseEntity.ok(ApiResponse.success(resp.getMessage(), resp));
    }

    @PostMapping("/{id}/abandon")
    @PreAuthorize("hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN')")
    @Operation(summary = "弃标决策")
    public ResponseEntity<ApiResponse<TenderBidResponse>> abandonBid(@PathVariable Long id, @Valid @RequestBody TenderAbandonRequest req, @AuthenticationPrincipal UserDetails user) {
        rejectDemoMutation(id);
        TenderBidResponse resp = tenderSubmissionService.abandonBid(id, req, resolveUserId(user));
        return ResponseEntity.ok(ApiResponse.success(resp.getMessage(), resp));
    }

    @GetMapping("/import-template")
    @PreAuthorize("hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BID_TEAM')")
    @Operation(summary = "下载标讯批量导入模板")
    public ResponseEntity<byte[]> downloadImportTemplate() {
        byte[] body = tenderImportService.generateTemplate();
        String filename = URLEncoder.encode("标讯批量导入模板.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"tender-import-template.xlsx\"; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(body);
    }

    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BID_TEAM')")
    @Idempotent
    @Operation(summary = "批量导入标讯")
    public ResponseEntity<ApiResponse<com.xiyu.bid.tender.dto.TenderImportResultDTO>> importTenders(@RequestParam("file") MultipartFile file, @AuthenticationPrincipal UserDetails user) {
        try {
            var result = tenderImportService.importFromExcel(file, resolveUserId(user));
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("成功导入 " + result.getSuccessCount() + " 条标讯", result));
        } catch (com.xiyu.bid.tender.service.TenderImportRollbackException ex) {
            return ResponseEntity.ok(ApiResponse.success("导入未通过校验", ex.getResult()));
        }
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "按状态筛选标讯")
    public ResponseEntity<ApiResponse<List<TenderDTO>>> getTendersByStatus(@PathVariable com.xiyu.bid.entity.Tender.Status status) {
        return ResponseEntity.ok(ApiResponse.success("查询成功", tenderQueryService.getTendersByStatus(status)));
    }

    @GetMapping("/source/{source}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "按来源筛选标讯")
    public ResponseEntity<ApiResponse<List<TenderDTO>>> getTendersBySource(@PathVariable String source) {
        return ResponseEntity.ok(ApiResponse.success("查询成功", tenderQueryService.getTendersBySource(InputSanitizer.sanitizeString(source, 100))));
    }

    @GetMapping("/statistics")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "标讯统计")
    public ResponseEntity<ApiResponse<Map<com.xiyu.bid.entity.Tender.Status, Long>>> getStatistics() {
        return ResponseEntity.ok(ApiResponse.success("查询成功", tenderQueryService.getTenderStatistics()));
    }

    private void sanitizeTenderRequest(TenderRequest r) { sanitizer.sanitize(r); }
    private void sanitizeTenderSearchCriteria(TenderSearchCriteria c) { sanitizer.sanitizeCriteria(c); }
    private boolean isDemoEntityId(Long id) { return demoModeService.isEnabled() && id != null && id < 0; }
    private void rejectDemoMutation(Long id) { if (isDemoEntityId(id)) throw new IllegalArgumentException("Demo records are read-only"); }
    private Long resolveUserId(UserDetails u) {
        if (u == null || u.getUsername() == null || u.getUsername().isBlank()) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED, "无法识别当前用户");
        return authService.resolveUserIdByUsername(u.getUsername().trim());
    }
}
