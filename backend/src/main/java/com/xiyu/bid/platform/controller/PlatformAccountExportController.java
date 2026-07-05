// Input: PlatformAccountExportService
// Output: REST API endpoints for platform account export
// Pos: Controller/控制器层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.platform.controller;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.platform.service.PlatformAccountExportService;
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
 * 平台账户批量导出端点。从 PlatformAccountController 拆出以满足 line-budget ≤300 约束。
 *
 * <p>权限：类级 hasAnyRole('ADMIN', 'MANAGER')，与批量导入/下载模板接口一致。
 * 导出包含明文密码，需更高权限门槛。
 */
@RestController
@RequestMapping("/api/platform/accounts")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
public class PlatformAccountExportController {

    private final PlatformAccountExportService exportService;

    /**
     * 批量导出平台账户台账 Excel。
     *
     * <p>导出模式（前端二选一）：
     * <ul>
     *   <li>selectedIds 非空 → 按 ID 集合导出选中项</li>
     *   <li>selectedIds 为空 → 导出全部账户</li>
     * </ul>
     *
     * <p>密码字段输出明文（用户明确需求），调用方已受 @PreAuthorize 限制。
     *
     * @param selectedIds 选中的账户 ID（逗号分隔，可选）
     * @return .xlsx 文件下载
     */
    @GetMapping("/export")
    @Auditable(action = "EXPORT", entityType = "PlatformAccount", description = "批量导出平台账户台账")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String selectedIds) {
        Set<Long> idSet = parseSelectedIds(selectedIds);
        byte[] excel = exportService.exportToExcel(idSet);
        String filename = URLEncoder.encode(
                "平台账户台账_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".xlsx",
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
            }
        }
        return ids;
    }
}
