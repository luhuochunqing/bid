package com.xiyu.bid.integration.external;

import com.xiyu.bid.apikey.application.ApiKeyService;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.service.AuthService;
import com.xiyu.bid.tender.dto.TenderDTO;
import com.xiyu.bid.tender.service.TenderMapper;
import com.xiyu.bid.tender.service.TenderQueryService;
import com.xiyu.bid.tender.service.TenderSearchCriteria;
import com.xiyu.bid.util.InputSanitizer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 外部标讯同步接口（接口规范 v2.0）。
 * 路径: /api/integration/tenders
 * 认证方式: X-API-Key Header（由 ApiKeyAuthenticationFilter 处理）。
 * scope 要求: tender:read / tender:write
 */
@RestController
@Tag(name = "标讯同步（外部API v2.0）", description = "第三方系统对接接口，接口规范 v2.0，通过 X-API-Key 认证")
@RequestMapping("/api/integration/tenders")
@PreAuthorize("hasRole('EXTERNAL_API')")
@RequiredArgsConstructor
@Slf4j
public class TenderIntegrationController {

    private final TenderQueryService tenderQueryService;
    private final TenderIntegrationService tenderIntegrationService;
    private final TenderMapper tenderMapper;
    private final ApiKeyService apiKeyService;
    private final AuthService authService;
    private final TenderRepository tenderRepository;

    // ── 接口一：标讯列表查询 ───────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "标讯列表查询", description = "模糊搜索 + 多维筛选，返回分页结果")
    public ResponseEntity<ApiResponse<Map<String, Object>>> listTenders(
            @ModelAttribute TenderSearchCriteria criteria) {
        log.info("INTEGRATION GET /api/integration/tenders - keyword={} page={} size={}",
                criteria.getKeyword(), criteria.getPage(), criteria.getSize());
        sanitizeCriteria(criteria);
        int safeSize = Math.min(Math.max(criteria.getSize(), 1), 100);
        int safePage = Math.max(criteria.getPage(), 0);
        Page<TenderDTO> page = tenderQueryService.searchTendersPaged(
                criteria, PageRequest.of(safePage, safeSize));

        // 归一化 source + 组装 contactInfo
        page.getContent().forEach(dto -> {
            if (dto.getSourceType() != null) {
                switch (dto.getSourceType()) {
                    case MANUAL_SINGLE:
                    case BULK_IMPORT:
                        dto.setSource("人工录入");
                        break;
                    case CRM_OPPORTUNITY:
                        dto.setSource("CRM创建");
                        break;
                    case EXTERNAL_PLATFORM:
                        dto.setSource("第三方平台");
                        break;
                }
            }
            // 联系人数组，与标讯详情接口格式一致
            dto.setContactInfo(tenderMapper.buildContactsFromDTO(dto));
        });

        Map<String, Object> data = Map.of(
                "content", page.getContent(),
                "totalElements", page.getTotalElements(),
                "totalPages", page.getTotalPages(),
                "page", safePage,
                "size", safeSize,
                "hasNext", page.hasNext(),
                "hasPrevious", page.hasPrevious()
        );
        return ResponseEntity.ok(ApiResponse.success("查询成功", data));
    }

    // ── 接口二：标讯创建（幂等推送）────────────────────────────────────────

    @PostMapping("/push")
    @Operation(summary = "标讯创建（幂等推送）", description = "按 (sourceSystem, sourceId) 幂等去重，无匹配时创建")
    public ResponseEntity<ApiResponse<TenderPushResponse>> pushTender(
            @Valid @RequestBody TenderPushRequest request) {
        log.info("INTEGRATION POST /api/integration/tenders/push - sourceSystem={} sourceId={} title={}",
                request.getSourceSystem(), request.getSourceId(), request.getTitle());

        Long userId = resolveApiKeyUserId();
        TenderPushResponse response = tenderIntegrationService.pushTender(request, userId);

        String status = response.getStatus();
        if ("DUPLICATE".equals(status)) {
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create("/api/integration/tenders/" + request.getSourceSystem() + "/" + request.getSourceId()));
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .headers(headers)
                    .body(ApiResponse.error(409,
                            "标讯 sourceId=" + request.getSourceId() + " 已存在，如需覆盖请传入 forceUpdate=true",
                            response));
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create("/api/integration/tenders/" + request.getSourceSystem() + "/" + request.getSourceId()));
        if ("UPDATED".equals(status)) {
            return ResponseEntity.ok(ApiResponse.success("标讯推送接收成功（已覆盖更新）", response));
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .headers(headers)
                .body(ApiResponse.created("标讯推送接收成功", response));
    }

    // ── 接口三：标讯修改 ───────────────────────────────────────────────────

    @PutMapping("/{sourceSystem}/{sourceId}")
    @Operation(summary = "标讯修改", description = "按 (sourceSystem, sourceId) 定位并更新字段")
    public ResponseEntity<ApiResponse<TenderDTO>> updateTender(
            @PathVariable String sourceSystem,
            @PathVariable String sourceId,
            @Valid @RequestBody TenderUpdateRequest request) {
        log.info("INTEGRATION PUT /api/integration/tenders/{}/{} - fields present",
                sourceSystem, sourceId);
        TenderDTO updated = tenderIntegrationService.updateByExternalId(
                sourceSystem, sourceId, request);
        return ResponseEntity.ok(ApiResponse.success("标讯更新成功", updated));
    }

    // ── 接口四：标讯详情 ───────────────────────────────────────────────────

    @GetMapping("/{sourceSystem}/{sourceId}")
    @Operation(summary = "标讯详情", description = "按 tenderId 或 (sourceSystem, sourceId) 查询单条标讯完整信息")
    public ResponseEntity<ApiResponse<TenderDTO>> getTender(
            @PathVariable String sourceSystem,
            @PathVariable String sourceId,
            @RequestParam(required = false) Long tenderId) {
        log.info("INTEGRATION GET /api/integration/tenders/{}/{} tenderId={}", sourceSystem, sourceId, tenderId);
        TenderDTO tender = tenderIntegrationService.getByExternalId(sourceSystem, sourceId, tenderId);
        return ResponseEntity.ok(ApiResponse.success("查询成功", tender));
    }

    // ── 内部方法 ────────────────────────────────────────────────────────────

    private void sanitizeCriteria(TenderSearchCriteria criteria) {
        if (criteria.getKeyword() != null) {
            criteria.setKeyword(InputSanitizer.sanitizeString(criteria.getKeyword(), 200));
        }
    }

    /**
     * 从 Security Context 中的 principal 解析 API Key 对应的用户 ID。
     * 用于创建标讯时写入 creatorId。
     */
    private Long resolveApiKeyUserId() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
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
}
