// Input: HTTP 请求、路径参数、认证上下文和 DTO
// Output: 标准化 API 响应和用例入口
// Pos: Controller/接口适配层
// 维护声明: 仅维护协议适配与参数校验；业务规则下沉到 service.
package com.xiyu.bid.tender.controller;

import com.xiyu.bid.ai.service.AiDeepCapabilityService;
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
import com.xiyu.bid.tender.service.TenderAuditService;
import com.xiyu.bid.tender.service.TenderCommandService;
import com.xiyu.bid.tender.service.TenderImportService;
import com.xiyu.bid.tender.service.TenderMapper;
import com.xiyu.bid.tender.service.TenderQueryService;
import com.xiyu.bid.tender.service.TenderSearchCriteria;
import com.xiyu.bid.util.InputSanitizer;
import com.xiyu.bid.annotation.DataScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import com.xiyu.bid.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.xiyu.bid.entity.AuditLog;
import com.xiyu.bid.ai.dto.TenderAiAnalysisDTO;

@RestController
@Tag(name = "标讯管理", description = "标讯（招标信息）CRUD、分页搜索与业务操作（投标/弃标/AI分析等）")
@RequestMapping("/api/tenders")
@RequiredArgsConstructor
@Slf4j
public class TenderController {

    private final TenderQueryService tenderQueryService;
    private final TenderCommandService tenderCommandService;
    private final TenderMapper tenderMapper;
    private final TenderImportService tenderImportService;
    private final AiDeepCapabilityService aiDeepCapabilityService;
    private final DemoModeService demoModeService;
    private final DemoDataProvider demoDataProvider;
    private final DemoFusionService demoFusionService;
    private final TenderAuditService tenderAuditService;
    private final AuthService authService;
    private final TenderRequestSanitizer sanitizer = new TenderRequestSanitizer();

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @DataScope
    @Operation(summary = "标讯列表查询（分页）",
        description = "模糊搜索（keyword 匹配标题/业主单位/代理/描述）和精准筛选（status/source/region/industry/budget/deadline 等），返回分页结果。")
    public ResponseEntity<ApiResponse<PagedResult<TenderDTO>>> getAllTenders(
            @ModelAttribute TenderSearchCriteria criteria) {
        log.info("GET /api/tenders - Searching tenders paged, criteria={}", criteria);
        sanitizeTenderSearchCriteria(criteria);
        Page<TenderDTO> page = tenderQueryService.searchTendersPaged(criteria, PageRequest.of(Math.max(criteria.getPage(), 0), criteria.getSize() > 0 ? criteria.getSize() : 20));
        PagedResult<TenderDTO> pagedResult = PagedResult.of(page.getContent(), page.getTotalElements(), page.getNumber(), page.getSize());
        if (demoModeService.isEnabled()) {
            List<TenderDTO> demoList = demoDataProvider.getDemoTenders();
            List<TenderDTO> merged = demoFusionService.mergeByKey(
                    pagedResult.content(), demoList, TenderDTO::getId);
            pagedResult = PagedResult.of(merged, pagedResult.totalElements(), pagedResult.pageNumber(), pagedResult.pageSize());
        }
        return ResponseEntity.ok(ApiResponse.success("查询成功", pagedResult));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Operation(summary = "标讯详情查询", description = "查询单条标讯的完整信息，包括基本信息、评估状态、分配人、AI 评分等。")
    public ResponseEntity<ApiResponse<TenderDTO>> getTenderById(@PathVariable Long id) {
        log.info("GET /api/tenders/{} - Fetching tender", id);
        if (isDemoEntityId(id)) {
            return ResponseEntity.ok(ApiResponse.success("查询成功",
                    demoDataProvider.findDemoTenderById(id).orElseThrow(
                            () -> new com.xiyu.bid.exception.ResourceNotFoundException("Tender", id.toString()))));
        }
        TenderDTO tender = tenderQueryService.getTenderById(id);
        return ResponseEntity.ok(ApiResponse.success("查询成功", tender));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Idempotent
    @Operation(summary = "创建标讯",
        description = "创建新标讯。如存在重复（相同标题 + purchaserHash + source），返回已有记录而非重复创建。去重字段：标题、业主哈希、来源平台。")
    public ResponseEntity<ApiResponse<TenderDTO>> createTender(
            @Valid @RequestBody TenderRequest tenderRequest,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("POST /api/tenders - Creating new tender: {}", tenderRequest.getTitle());
        sanitizeTenderRequest(tenderRequest);
        TenderDTO tenderDTO = tenderMapper.toDTO(tenderRequest);
        TenderDTO createdTender = tenderCommandService.createTender(tenderDTO, resolveUserId(userDetails));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("创建成功", createdTender));
    }

    @GetMapping("/import-template")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Operation(summary = "下载标讯批量导入模板", description = "生成 Excel 模板文件，供批量导入使用。")
    public ResponseEntity<byte[]> downloadImportTemplate() {
        log.info("GET /api/tenders/import-template - Generating bulk import template");
        byte[] body = tenderImportService.generateTemplate();
        String filename = URLEncoder.encode("标讯批量导入模板.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"tender-import-template.xlsx\"; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }

    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Idempotent
    @Operation(summary = "批量导入标讯", description = "通过 Excel 文件批量导入标讯，支持去重校验。校验未通过时整批回滚。")
    public ResponseEntity<ApiResponse<com.xiyu.bid.tender.dto.TenderImportResultDTO>> importTenders(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("POST /api/tenders/import - Importing tenders, originalName={}, size={}",
                file == null ? null : file.getOriginalFilename(), file == null ? 0 : file.getSize());
        try {
            var result = tenderImportService.importFromExcel(file, resolveUserId(userDetails));
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("成功导入 " + result.getSuccessCount() + " 条标讯", result));
        } catch (com.xiyu.bid.tender.service.TenderImportRollbackException ex) {
            log.info("标讯批量导入校验未通过，已整批回滚 failureCount={}",
                    ex.getResult() == null ? 0 : ex.getResult().getFailureCount());
            return ResponseEntity.ok(ApiResponse.success("导入未通过校验，请按错误列表修正后重试", ex.getResult()));
        }
    }

    @GetMapping("/{id}/ai-analysis")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Operation(summary = "查询标讯 AI 分析结果", description = "获取指定标讯最近一次 AI 分析的中标概率评估与建议。")
    public ResponseEntity<ApiResponse<TenderAiAnalysisDTO>> getTenderAiAnalysis(@PathVariable Long id) {
        log.info("GET /api/tenders/{}/ai-analysis - Fetching tender AI analysis", id);
        if (isDemoEntityId(id)) {
            TenderDTO tender = demoDataProvider.findDemoTenderById(id).orElseThrow(
                    () -> new com.xiyu.bid.exception.ResourceNotFoundException("Tender", id.toString()));
            TenderAiAnalysisDTO dto = TenderAiAnalysisDTO.builder()
                    .tenderId(tender.getId())
                    .winScore(tender.getAiScore() == null ? 80 : tender.getAiScore())
                    .suggestion("Demo 标讯分析：重点关注资质响应、交付周期和付款条件。")
                    .dimensionScores(List.of()).risks(List.of()).autoTasks(List.of()).build();
            return ResponseEntity.ok(ApiResponse.success("查询成功", dto));
        }
        Optional<TenderAiAnalysisDTO> analysis = aiDeepCapabilityService.getLatestTenderAnalysis(id);
        if (analysis.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(404, "AI 分析结果不存在"));
        }
        return ResponseEntity.ok(ApiResponse.success("查询成功", analysis.get()));
    }

    @PostMapping("/{id}/ai-analysis")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Operation(summary = "触发标讯 AI 分析", description = "异步调用 AI 能力对标讯进行深度分析，生成中标概率评估与建议。")
    public ResponseEntity<ApiResponse<TenderAiAnalysisDTO>> createTenderAiAnalysis(@PathVariable Long id) {
        log.info("POST /api/tenders/{}/ai-analysis - Generating tender AI analysis", id);
        rejectDemoMutation(id);
        TenderAiAnalysisDTO analysis = aiDeepCapabilityService.analyzeTender(id, null);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("分析任务已提交", analysis));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Operation(summary = "修改标讯", description = "更新指定标讯的基本信息及评估表信息（tenderInfo）。支持幂等更新。")
    public ResponseEntity<ApiResponse<TenderDTO>> updateTender(
            @PathVariable Long id, @Valid @RequestBody TenderRequest tenderRequest,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("PUT /api/tenders/{} - Updating tender", id);
        rejectDemoMutation(id);
        sanitizeTenderRequest(tenderRequest);
        TenderDTO tenderDTO = tenderMapper.toDTO(tenderRequest);
        TenderDTO updatedTender = tenderCommandService.updateTender(id, tenderDTO, resolveUserId(userDetails));
        return ResponseEntity.ok(ApiResponse.success("更新成功", updatedTender));
    }

    @GetMapping("/{id}/audit-logs")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Operation(summary = "标讯审计日志", description = "查询指定标讯的全部操作审计记录。")
    public ResponseEntity<ApiResponse<List<AuditLog>>> getAuditLogs(@PathVariable Long id) {
        log.info("GET /api/tenders/{}/audit-logs", id);
        return ResponseEntity.ok(ApiResponse.success("查询成功", tenderAuditService.getAuditLogs(id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Operation(summary = "删除标讯", description = "ADMIN/MANAGER 可删除任意未分配标讯；STAFF 仅可删除自己创建的未分配标讯。")
    public ResponseEntity<ApiResponse<Void>> deleteTender(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("DELETE /api/tenders/{} - Deleting tender", id);
        rejectDemoMutation(id);
        tenderCommandService.deleteTender(id, resolveUserId(userDetails));
        return ResponseEntity.ok(ApiResponse.success("删除成功", null));
    }

    @PostMapping("/{id}/analyze")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Operation(summary = "触发标讯 AI 分析", description = "调用 AI 能力对待分析标讯进行深度分析，生成中标概率评估与建议。")
    public ResponseEntity<ApiResponse<TenderDTO>> analyzeTender(@PathVariable Long id) {
        log.info("POST /api/tenders/{}/analyze - Analyzing tender", id);
        rejectDemoMutation(id);
        return ResponseEntity.ok(ApiResponse.success("分析完成", tenderCommandService.analyzeTender(id)));
    }

    @PostMapping("/{id}/participate")
    @Operation(summary = "投标决策", description = "将标讯标记为投标。实例级权限：调用方必须是最新分配人。")
    public ResponseEntity<ApiResponse<TenderBidResponse>> participateBid(
            @PathVariable Long id, @AuthenticationPrincipal UserDetails userDetails) {
        log.info("POST /api/tenders/{}/participate - Participating bid", id);
        rejectDemoMutation(id);
        TenderBidResponse response = tenderCommandService.participateBid(id, resolveUserId(userDetails));
        return ResponseEntity.ok(ApiResponse.success(response.getMessage(), response));
    }

    @PostMapping("/{id}/abandon")
    @Operation(summary = "弃标决策", description = "将标讯标记为弃标，并记录弃标原因。实例级权限：调用方必须是最新分配人。")
    public ResponseEntity<ApiResponse<TenderBidResponse>> abandonBid(
            @PathVariable Long id, @Valid @RequestBody TenderAbandonRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("POST /api/tenders/{}/abandon - Abandoning tender", id);
        rejectDemoMutation(id);
        TenderBidResponse response = tenderCommandService.abandonBid(id, req, resolveUserId(userDetails));
        return ResponseEntity.ok(ApiResponse.success(response.getMessage(), response));
    }


    @GetMapping("/status/{status}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Operation(summary = "按状态筛选标讯",
        description = "根据标讯状态（PENDING_ASSIGNMENT/TRACKING/EVALUATED/BIDDING/WON/LOST/ABANDONED）返回列表。")
    public ResponseEntity<ApiResponse<List<TenderDTO>>> getTendersByStatus(
            @PathVariable com.xiyu.bid.entity.Tender.Status status) {
        log.info("GET /api/tenders/status/{} - Fetching tenders by status", status);
        return ResponseEntity.ok(ApiResponse.success("查询成功", tenderQueryService.getTendersByStatus(status)));
    }

    @GetMapping("/source/{source}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Operation(summary = "按来源筛选标讯", description = "根据标讯来源平台返回列表（已做 XSS 过滤）。")
    public ResponseEntity<ApiResponse<List<TenderDTO>>> getTendersBySource(@PathVariable String source) {
        log.info("GET /api/tenders/source/{} - Fetching tenders by source", source);
        String sanitizedSource = InputSanitizer.sanitizeString(source, 100);
        return ResponseEntity.ok(ApiResponse.success("查询成功", tenderQueryService.getTendersBySource(sanitizedSource)));
    }

    @GetMapping("/statistics")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "标讯统计",
        description = "返回各状态（PENDING_ASSIGNMENT/TRACKING/EVALUATED/BIDDING/WON/LOST/ABANDONED）的标讯数量。")
    public ResponseEntity<ApiResponse<Map<com.xiyu.bid.entity.Tender.Status, Long>>> getStatistics() {
        log.info("GET /api/tenders/statistics - Fetching tender statistics");
        return ResponseEntity.ok(ApiResponse.success("查询成功", tenderQueryService.getTenderStatistics()));
    }

    private void sanitizeTenderRequest(TenderRequest request) { sanitizer.sanitize(request); }
    private void sanitizeTenderSearchCriteria(TenderSearchCriteria criteria) { sanitizer.sanitizeCriteria(criteria); }

    private boolean isDemoEntityId(Long id) {
        return demoModeService.isEnabled() && id != null && id < 0;
    }

    private void rejectDemoMutation(Long id) {
        if (isDemoEntityId(id)) throw new IllegalArgumentException("Demo records are read-only in e2e mode");
    }

    private Long resolveUserId(UserDetails userDetails) {
        if (userDetails == null || userDetails.getUsername() == null || userDetails.getUsername().isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "无法识别当前用户");
        }
        return authService.resolveUserIdByUsername(userDetails.getUsername().trim());
    }
}
