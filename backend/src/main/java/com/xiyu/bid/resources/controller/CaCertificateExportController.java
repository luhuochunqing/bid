package com.xiyu.bid.resources.controller;

import com.xiyu.bid.resources.service.CaCertificateExportService;
import com.xiyu.bid.resources.service.CaCertificateExportService.CaExportFilters;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * CO-466: CA 证书批量导出端点。从 CaCertificateController 拆出以满足 line-budget ≤300 约束。
 *
 * <p>权限：类级 hasAuthority('resource') 兜底，覆盖 admin/bidAdmin/bid-TeamLeader/bid-Team/bid-projectLeader。
 * 前端 canCreate 已做 UI 收敛，避免新增 hasAnyRole 触发 Constitution VI 门禁（specs/024-preauthorize-unification）。
 */
@RestController
@RequestMapping("/api/ca-certificates")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('resource')")
public class CaCertificateExportController {

    private final CaCertificateExportService exportService;

    /**
     * 批量导出 CA 证书台账 Excel。
     *
     * <p>导出模式（前端二选一）：
     * <ul>
     *   <li>selectedIds 非空 → 按 ID 集合导出选中项（忽略筛选条件）</li>
     *   <li>selectedIds 为空 → 按筛选条件导出全部匹配数据</li>
     * </ul>
     *
     * <p>密码字段输出明文（用户明确需求），调用方已受 @PreAuthorize 限制。
     *
     * @param status       证书状态筛选
     * @param borrowStatus 借用状态筛选
     * @param keyword      关键词（持有人/颁发机构/保管员）
     * @param caType       CA 类型
     * @param sealType     印章类型
     * @param selectedIds  选中的 CA ID（逗号分隔，可选）
     * @return .xlsx 文件下载
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String borrowStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String caType,
            @RequestParam(required = false) String sealType,
            @RequestParam(required = false) String selectedIds) {
        CaExportFilters filters = new CaExportFilters(status, borrowStatus, keyword, caType, sealType);
        Set<Long> idSet = parseSelectedIds(selectedIds);
        byte[] excel = exportService.exportToExcel(filters, idSet);
        String filename = URLEncoder.encode(
                "CA证书台账_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".xlsx",
                StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .body(excel);
    }

    /** 解析前端传来的逗号分隔 ID 字符串，过滤空值与非法值。 */
    private static Set<Long> parseSelectedIds(String selectedIds) {
        if (selectedIds == null || selectedIds.isBlank()) return Collections.emptySet();
        Set<Long> ids = new LinkedHashSet<>();
        for (String token : selectedIds.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) continue;
            try {
                ids.add(Long.parseLong(trimmed));
            } catch (NumberFormatException ignored) {
                // 非法 ID 静默跳过，不中断整个导出
            }
        }
        return ids;
    }
}
