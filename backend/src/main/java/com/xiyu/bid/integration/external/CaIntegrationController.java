package com.xiyu.bid.integration.external;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.resources.dto.CaCertificateDTO;
import com.xiyu.bid.resources.service.CaBusinessException;
import com.xiyu.bid.resources.service.CaCertificateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

/**
 * CA 证书对外查询接口（外部 API v2.0）。
 * 路径: /api/integration/ca-certificates
 * 认证方式: X-API-Key Header（由 ApiKeyAuthenticationFilter 处理）
 * scope 要求: ca:read
 *
 * <p>复用 {@link CaCertificateService} 的查询能力，密码字段默认脱敏为 ******。
 * 对外接口不支持密码解密、借用、下架等写操作，仅提供只读查询。
 */
@RestController
@Tag(name = "CA 证书查询（外部API v2.0）", description = "第三方系统对接接口，通过 X-API-Key 认证，scope=ca:read")
@RequestMapping("/api/integration/ca-certificates")
@PreAuthorize("hasAuthority('SCOPE_CA_READ')")
@RequiredArgsConstructor
@Slf4j
public class CaIntegrationController {

    private final CaCertificateService caCertificateService;

    /**
     * 分页查询 CA 证书列表，支持按状态、借用状态、关键词及 CA 类型等条件筛选。
     * 每条记录为完整的 CA 证书对象，密码字段默认脱敏。
     */
    @GetMapping
    @Operation(summary = "CA 证书列表查询", description = "支持 status/borrowStatus/keyword/caType/sealType 筛选 + 分页")
    public ResponseEntity<ApiResponse<Map<String, Object>>> listCaCertificates(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String borrowStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String caType,
            @RequestParam(required = false) String sealType,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            org.springframework.data.domain.Pageable pageable) {
        log.info("INTEGRATION GET /api/integration/ca-certificates - status={} borrowStatus={} keyword={} caType={} sealType={}",
                status, borrowStatus, keyword, caType, sealType);
        // 限制分页大小上限，避免外部系统拉取过多数据
        int safeSize = Math.min(Math.max(pageable.getPageSize(), 1), 100);
        int safePage = Math.max(pageable.getPageNumber(), 0);
        Page<CaCertificateDTO> page = caCertificateService.list(
                status, borrowStatus, keyword, caType, sealType,
                PageRequest.of(safePage, safeSize, pageable.getSortOr(Sort.by(Sort.Direction.DESC, "createdAt"))));

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

    /**
     * CA 证书统计概览，返回 total/expiring/expired/borrowed 计数。
     */
    @GetMapping("/overview")
    @Operation(summary = "CA 证书统计概览", description = "返回 total/expiring/expired/borrowed 计数")
    public ResponseEntity<ApiResponse<Map<String, Long>>> overview() {
        log.info("INTEGRATION GET /api/integration/ca-certificates/overview");
        Map<String, Long> data = caCertificateService.getOverview();
        return ResponseEntity.ok(ApiResponse.success("查询成功", data));
    }

    /**
     * 按 ID 查询单条 CA 证书完整信息，密码字段默认脱敏。
     */
    @GetMapping("/{id}")
    @Operation(summary = "CA 证书详情", description = "按 ID 查询单条 CA 证书完整信息")
    public ResponseEntity<ApiResponse<CaCertificateDTO>> getCaCertificate(@PathVariable Long id) {
        log.info("INTEGRATION GET /api/integration/ca-certificates/{}", id);
        CaCertificateDTO dto = caCertificateService.getById(id);
        return ResponseEntity.ok(ApiResponse.success("查询成功", dto));
    }

    // ========== 异常处理（本地 handler，避免 integration 包不被 CaExceptionHandler 扫描） ==========

    /**
     * 处理 CA 业务异常（如 ID 不存在），返回 404 + 标准 ApiResponse 格式。
     * <p>CaExceptionHandler 仅扫描 {@code com.xiyu.bid.resources} 包，
     * 本 Controller 在 integration 包下，需自行兜底，否则会被 GlobalExceptionHandler
     * 当通用异常吞成 500 "系统繁忙"。
     */
    @ExceptionHandler(CaBusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleCaBusiness(CaBusinessException ex) {
        String code = ex.getErrorCode();
        HttpStatus status = switch (code == null ? "" : code) {
            case "AUTH_REQUIRED" -> HttpStatus.UNAUTHORIZED;
            case "PERMISSION_DENIED" -> HttpStatus.FORBIDDEN;
            case "NOT_FOUND" -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.NOT_FOUND; // "CA证书不存在" 等通用业务未命中场景
        };
        log.warn("INTEGRATION CA business exception: [{}] {}", code, ex.getMessage());
        return ResponseEntity.status(status)
                .body(ApiResponse.error(status.value(), ex.getMessage()));
    }

    /**
     * 处理路径参数类型转换失败（如 /{id} 传了非数字 "abc"），返回 400 友好提示。
     * 兜底避免被 GlobalExceptionHandler#handleGlobalException 吞成 500。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String name = ex.getName();
        String expected = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "参数";
        String msg = String.format("参数「%s」格式错误，期望类型：%s", name, expected);
        log.warn("INTEGRATION CA type mismatch: name={} value={} expected={}", name, ex.getValue(), expected);
        return ResponseEntity.badRequest().body(ApiResponse.error(400, msg));
    }
}
