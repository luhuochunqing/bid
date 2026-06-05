// Input: HTTP 请求、路径参数、认证上下文和 DTO
// Output: 标准化 API 响应和用例入口
// Pos: Controller/接口适配层
// 维护声明: 仅维护协议适配与参数校验；业务规则下沉到 service.
package com.xiyu.bid.casework.controller;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.casework.application.service.CaseCrudAppService;
import com.xiyu.bid.casework.application.service.CasePromotionAppService;
import com.xiyu.bid.casework.application.service.CaseReferenceAppService;
import com.xiyu.bid.casework.application.service.CaseSearchAppService;
import com.xiyu.bid.casework.application.service.CaseShareAppService;
import com.xiyu.bid.casework.domain.model.CaseSearchCriteria;
import com.xiyu.bid.casework.dto.CaseDTO;
import com.xiyu.bid.casework.dto.CasePromoteFromProjectRequest;
import com.xiyu.bid.casework.dto.CaseRecommendationDTO;
import com.xiyu.bid.casework.dto.CaseReferenceRecordCreateRequest;
import com.xiyu.bid.casework.dto.CaseReferenceRecordDTO;
import com.xiyu.bid.casework.dto.CaseSearchOptionsDTO;
import com.xiyu.bid.casework.dto.CaseSearchResultDTO;
import com.xiyu.bid.casework.dto.CaseShareRecordCreateRequest;
import com.xiyu.bid.casework.dto.CaseShareRecordDTO;
import com.xiyu.bid.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/knowledge/cases")
@RequiredArgsConstructor
public class CaseController {

    private final CaseCrudAppService caseCrudAppService;
    private final CaseSearchAppService caseSearchAppService;
    private final CasePromotionAppService casePromotionAppService;
    private final CaseShareAppService caseShareAppService;
    private final CaseReferenceAppService caseReferenceAppService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Auditable(action = "CREATE", entityType = "Case", description = "创建案例")
    public ResponseEntity<ApiResponse<CaseDTO>> createCase(@Valid @RequestBody CaseDTO dto) {
        CaseRequestSanitizer.sanitizeCase(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Case created successfully", caseCrudAppService.create(dto)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "READ", entityType = "Case", description = "获取案例分页列表")
    public ResponseEntity<ApiResponse<CaseSearchResultDTO>> getAllCases(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) CaseDTO.Industry industry,
            @RequestParam(required = false) String productLine,
            @RequestParam(required = false) CaseDTO.Outcome outcome,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) BigDecimal amountMin,
            @RequestParam(required = false) BigDecimal amountMax,
            @RequestParam(required = false) List<String> tags,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String visibility,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(defaultValue = "latest") String sort) {
        CaseSearchCriteria criteria = CaseRequestSanitizer.sanitizeSearchCriteria(
                keyword, industry, productLine, outcome, year, amountMin, amountMax, tags, status, visibility, page, pageSize, sort);
        return ResponseEntity.ok(ApiResponse.success("Cases retrieved successfully", caseSearchAppService.search(criteria)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "READ", entityType = "Case", description = "根据ID获取案例")
    public ResponseEntity<ApiResponse<CaseDTO>> getCaseById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Case retrieved successfully", caseCrudAppService.findById(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Auditable(action = "UPDATE", entityType = "Case", description = "更新案例")
    public ResponseEntity<ApiResponse<CaseDTO>> updateCase(@PathVariable Long id, @Valid @RequestBody CaseDTO dto) {
        CaseRequestSanitizer.sanitizeCase(dto);
        return ResponseEntity.ok(ApiResponse.success("Case updated successfully", caseCrudAppService.update(id, dto)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Auditable(action = "DELETE", entityType = "Case", description = "删除案例")
    public ResponseEntity<Void> deleteCase(@PathVariable Long id) {
        caseCrudAppService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/industry/{industry}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "READ", entityType = "Case", description = "根据行业获取案例")
    public ResponseEntity<ApiResponse<List<CaseDTO>>> getCasesByIndustry(@PathVariable CaseDTO.Industry industry) {
        return ResponseEntity.ok(ApiResponse.success("Cases retrieved successfully", caseCrudAppService.findByIndustry(industry)));
    }

    @GetMapping("/outcome/{outcome}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "READ", entityType = "Case", description = "根据结果获取案例")
    public ResponseEntity<ApiResponse<List<CaseDTO>>> getCasesByOutcome(@PathVariable CaseDTO.Outcome outcome) {
        return ResponseEntity.ok(ApiResponse.success("Cases retrieved successfully", caseCrudAppService.findByOutcome(outcome)));
    }

    @GetMapping("/search/options")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "READ", entityType = "Case", description = "获取案例搜索选项")
    public ResponseEntity<ApiResponse<CaseSearchOptionsDTO>> getSearchOptions() {
        return ResponseEntity.ok(ApiResponse.success("Case search options retrieved successfully", caseSearchAppService.getSearchOptionsDTO()));
    }

    @GetMapping("/{id}/related")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "READ", entityType = "Case", description = "获取相关推荐案例")
    public ResponseEntity<ApiResponse<List<CaseRecommendationDTO>>> getRelatedCases(
            @PathVariable Long id,
            @RequestParam(defaultValue = "5") Integer limit) {
        return ResponseEntity.ok(ApiResponse.success(
                "Related cases retrieved successfully",
                caseSearchAppService.getRelatedCases(id, Math.max(limit == null ? 5 : limit, 1))));
    }

    @PostMapping("/promote-from-project")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Auditable(action = "CREATE", entityType = "Case", description = "从项目快照晋升案例")
    public ResponseEntity<ApiResponse<CaseDTO>> promoteFromProject(@Valid @RequestBody CasePromoteFromProjectRequest request) {
        CaseRequestSanitizer.sanitizePromotion(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Case promoted successfully", casePromotionAppService.promoteFromProject(request)));
    }

    @GetMapping("/{id}/share-records")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "READ", entityType = "Case", description = "获取案例分享记录")
    public ResponseEntity<ApiResponse<List<CaseShareRecordDTO>>> getCaseShareRecords(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Case share records retrieved successfully", caseShareAppService.getShareRecords(id)));
    }

    @PostMapping("/{id}/share-records")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "CREATE", entityType = "Case", description = "创建案例分享记录")
    public ResponseEntity<ApiResponse<CaseShareRecordDTO>> createCaseShareRecord(@PathVariable Long id, @Valid @RequestBody CaseShareRecordCreateRequest request) {
        CaseRequestSanitizer.sanitizeShareRecord(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Case share record created successfully", caseShareAppService.createShareRecord(id, request)));
    }

    @GetMapping("/{id}/references")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "READ", entityType = "Case", description = "获取案例引用记录")
    public ResponseEntity<ApiResponse<List<CaseReferenceRecordDTO>>> getCaseReferenceRecords(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Case reference records retrieved successfully", caseReferenceAppService.getReferenceRecords(id)));
    }

    @PostMapping("/{id}/references")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "CREATE", entityType = "Case", description = "创建案例引用记录")
    public ResponseEntity<ApiResponse<CaseReferenceRecordDTO>> createCaseReferenceRecord(@PathVariable Long id, @Valid @RequestBody CaseReferenceRecordCreateRequest request) {
        CaseRequestSanitizer.sanitizeReferenceRecord(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Case reference record created successfully", caseReferenceAppService.createReferenceRecord(id, request)));
    }
}
