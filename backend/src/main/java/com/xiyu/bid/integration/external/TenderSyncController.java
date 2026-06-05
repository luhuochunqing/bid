package com.xiyu.bid.integration.external;

import com.xiyu.bid.apikey.application.ApiKeyService;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.service.AuthService;
import com.xiyu.bid.dto.ApiResponse;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import com.xiyu.bid.tender.dto.TenderDTO;
import com.xiyu.bid.tender.dto.TenderRequest;
import com.xiyu.bid.tender.service.TenderCommandService;
import com.xiyu.bid.tender.service.TenderMapper;
import com.xiyu.bid.tender.service.TenderQueryService;
import com.xiyu.bid.tender.service.TenderSearchCriteria;
import com.xiyu.bid.util.InputSanitizer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * CRM 外部 API — 标讯同步接口。
 * 认证方式：X-API-Key Header（由 ApiKeyAuthenticationFilter 处理）。
 * scope 要求: tender:read / tender:write
 */
@RestController
@Tag(name = "标讯同步（外部API）", description = "CRM 第三方系统对接接口，通过 X-API-Key 认证")
@RequestMapping("/api/external/tenders")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyAuthority('SCOPE_TENDER_READ', 'SCOPE_TENDER_WRITE')")
public class TenderSyncController {

    private final TenderQueryService tenderQueryService;
    private final TenderCommandService tenderCommandService;
    private final TenderMapper tenderMapper;
    private final ApiKeyService apiKeyService;
    private final AuthService authService;

    /**
     * 增量拉取标讯列表。SQL 级分页，支持 updatedSince + page/size。
     */
    @GetMapping
    @Operation(summary = "增量拉取标讯列表", description = "支持按 updatedSince 增量同步 + page/size 分页")
    public ResponseEntity<ApiResponse<Map<String, Object>>> listTenders(
            @ModelAttribute TenderSearchCriteria criteria) {
        log.info("EXTERNAL GET /api/external/tenders - updatedSince={} page={} size={}",
                criteria.getUpdatedSince(), criteria.getPage(), criteria.getSize());
        sanitizeCriteria(criteria);
        int safeSize = Math.min(Math.max(criteria.getSize(), 1), 200);
        int safePage = Math.max(criteria.getPage(), 0);

        Page<TenderDTO> page = tenderQueryService.searchTendersPaged(
                criteria, PageRequest.of(safePage, safeSize));

        Map<String, Object> data = Map.of(
                "content", (Object) page.getContent(),
                "totalCount", page.getTotalElements(),
                "page", safePage,
                "size", safeSize,
                "hasMore", page.hasNext()
        );
        return ResponseEntity.ok(ApiResponse.success("Tenders retrieved", data));
    }

    /**
     * 获取单条标讯详情。
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TenderDTO>> getTender(@PathVariable Long id) {
        log.info("EXTERNAL GET /api/external/tenders/{}", id);
        TenderDTO tender = tenderQueryService.getTenderById(id);
        return ResponseEntity.ok(ApiResponse.success("Tender retrieved", tender));
    }

    /**
     * CRM 推送商机 → 创建标讯。
     * scope 要求: tender:write
     */
    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_TENDER_WRITE')")
    public ResponseEntity<ApiResponse<TenderDTO>> createTender(
            @Valid @RequestBody TenderRequest request) {
        log.info("EXTERNAL POST /api/external/tenders - title={}", request.getTitle());
        sanitizeRequest(request);
        if (request.getSourceType() == null) {
            request.setSourceType(Tender.SourceType.CRM_OPPORTUNITY);
        }
        TenderDTO dto = tenderMapper.toDTO(request);
        Long userId = resolveApiKeyUserId();
        TenderDTO created = tenderCommandService.createTender(dto, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tender created from CRM opportunity", created));
    }

    /**
     * 从 API Key 的创建者用户名解析用户 ID。
     * 若无法解析（如 API Key 已删除或不匹配），返回 null，
     * createTender 仍会保存，只是 creatorId 为空。
     */
    private Long resolveApiKeyUserId() {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) return null;
        String principal = auth.getPrincipal().toString();
        if (!principal.startsWith("api-key:")) return null;
        Long apiKeyId;
        try {
            apiKeyId = Long.parseLong(principal.substring("api-key:".length()));
        } catch (NumberFormatException e) {
            log.warn("Malformed API key principal: {}", principal);
            return null;
        }
        var keyOpt = apiKeyService.findById(apiKeyId);
        if (keyOpt.isEmpty()) return null;
        String createdBy = keyOpt.get().getCreatedBy();
        if (createdBy == null || createdBy.isBlank()) return null;
        try {
            return authService.resolveUserIdByUsername(createdBy.trim());
        } catch (UsernameNotFoundException e) {
            log.warn("API key user '{}' not found: {}", createdBy, e.getMessage());
            return null;
        }
    }

    private void sanitizeRequest(TenderRequest request) {
        if (request.getTitle() != null) request.setTitle(InputSanitizer.sanitizeString(request.getTitle(), 500));
        if (request.getDescription() != null) request.setDescription(InputSanitizer.sanitizeString(request.getDescription(), 5000));
        if (request.getSource() != null) request.setSource(InputSanitizer.sanitizeString(request.getSource(), 200));
        if (request.getPurchaserName() != null) request.setPurchaserName(InputSanitizer.sanitizeString(request.getPurchaserName(), 255));
    }

    private void sanitizeCriteria(TenderSearchCriteria criteria) {
        if (criteria.getKeyword() != null) criteria.setKeyword(InputSanitizer.sanitizeString(criteria.getKeyword(), 200));
    }
}
