package com.xiyu.bid.performance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.config.PaginationConstants;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.performance.application.PerformanceBundleExportAppService;
import com.xiyu.bid.performance.application.command.PerformanceSearchCriteria;
import com.xiyu.bid.performance.infrastructure.persistence.entity.PerformanceExportTaskEntity;
import com.xiyu.bid.warehouse.controller.UserResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;

/**
 * 业绩合订本导出控制器 — 独立端点，与原 ZIP 导出入口分离（需求 §3）。
 *
 * <p>路径前缀：/api/knowledge/performance/bundle-export
 */
@RestController
@RequestMapping("/api/knowledge/performance/bundle-export")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("isAuthenticated()")
public class PerformanceBundleExportController {

    private static final String PERM = RoleProfileCatalog.PERFORMANCE_MANAGE_PERMISSION;
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FILENAME_DT_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final PerformanceBundleExportAppService exportAppService;
    private final UserResolver userResolver;
    private final ObjectMapper objectMapper;

    /**
     * 触发合订本导出任务（异步）。
     * <p>请求体：
     * <ul>
     *   <li>ids: 勾选模式（按指定业绩记录导出）</li>
     *   <li>criteria: 筛选模式（按筛选条件导出）</li>
     *   <li>attachmentTypes: 附件类型筛选</li>
     * </ul>
     */
    @PostMapping
    @PreAuthorize("hasAuthority('" + PERM + "')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> triggerExport(
            @RequestBody(required = false) BundleExportRequest request) {
        Long operatorId = userResolver.resolveCurrentUserId();
        if (operatorId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("未登录"));
        }
        Set<String> attachmentTypes = (request != null && request.attachmentTypes() != null)
                ? request.attachmentTypes() : Set.of();

        PerformanceBundleExportAppService.ExportTaskResult result;
        try {
            if (request != null && request.ids() != null && !request.ids().isEmpty()) {
                result = exportAppService.exportByIds(request.ids(), attachmentTypes, operatorId);
            } else {
                PerformanceSearchCriteria criteria = (request != null && request.criteria() != null)
                        ? request.criteria() : PerformanceSearchCriteria.empty();
                result = exportAppService.export(criteria, attachmentTypes, operatorId);
            }
        } catch (RejectedExecutionException e) {
            // 线程池满（AbortPolicy）：返回 503，提示用户稍后重试
            log.warn("业绩合订本导出线程池已满，拒绝任务: operatorId={}", operatorId);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("系统繁忙，已有多个导出任务在排队，请稍后重试"));
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("合订本导出任务已创建", Map.of("taskId", result.taskId())));
    }

    @GetMapping("/tasks")
    @PreAuthorize("hasAuthority('" + PERM + "')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> listExportTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {
        Long userId = userResolver.resolveCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("未登录"));
        }
        // 分页参数上限保护（项目规范：PaginationConstants.MAX_PAGE_SIZE=100）
        int safeSize = Math.min(Math.max(size, 1), PaginationConstants.MAX_PAGE_SIZE);
        Page<PerformanceExportTaskEntity> tasks = exportAppService.listTasks(
                userId, PageRequest.of(page, safeSize, Sort.by("createdAt").descending()));
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "content", tasks.getContent().stream().map(this::toTaskMap).toList(),
                "totalElements", tasks.getTotalElements(),
                "totalPages", tasks.getTotalPages(),
                "number", tasks.getNumber(),
                "size", tasks.getSize()
        )));
    }

    @GetMapping("/tasks/{taskId}/status")
    @PreAuthorize("hasAuthority('" + PERM + "')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getExportTaskStatus(@PathVariable Long taskId) {
        Long userId = userResolver.resolveCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("未登录"));
        }
        try {
            PerformanceExportTaskEntity task = exportAppService.getTaskStatus(taskId, userId);
            return ResponseEntity.ok(ApiResponse.success(toTaskMap(task)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/tasks/{taskId}/download")
    @PreAuthorize("hasAuthority('" + PERM + "')")
    public ResponseEntity<StreamingResponseBody> downloadExportFile(@PathVariable Long taskId) {
        Long userId = userResolver.resolveCurrentUserId();
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try {
            // 单次查询同时获取文件路径和任务实体（CO-602 PR 审查修复：避免重复查询）
            PerformanceBundleExportAppService.ExportFileResult result =
                    exportAppService.getExportFileWithTask(taskId, userId);
            String filename = buildDownloadFilename(result.task());
            long fileSize = Files.size(result.path());
            StreamingResponseBody body = out -> {
                try (InputStream in = Files.newInputStream(result.path())) {
                    in.transferTo(out);
                }
            };
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, buildContentDisposition(filename))
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .contentLength(fileSize)
                    .body(body);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // ========== 辅助方法 ==========

    private String buildDownloadFilename(PerformanceExportTaskEntity task) {
        String ts = task.getCompletedAt() != null
                ? task.getCompletedAt().format(FILENAME_DT_FMT)
                : LocalDateTime.now().format(FILENAME_DT_FMT);
        return "业绩合订本_" + ts + ".docx";
    }

    /**
     * 构造 RFC 5987 兼容的 Content-Disposition 头，支持中文文件名。
     * <p>同时提供 ASCII fallback（filename）和 UTF-8 编码（filename*），
     * 兼容旧浏览器和现代浏览器，避免中文乱码。
     */
    private String buildContentDisposition(String filename) {
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded;
    }

    private Map<String, Object> toTaskMap(PerformanceExportTaskEntity t) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", t.getId());
        map.put("status", t.getStatus().name());
        map.put("totalCount", t.getTotalCount() != null ? t.getTotalCount() : 0);
        map.put("downloadUrl", t.getDownloadUrl() != null ? t.getDownloadUrl() : "");
        map.put("expiresAt", formatDt(t.getExpiresAt()));
        map.put("createdAt", formatDt(t.getCreatedAt()));
        map.put("completedAt", formatDt(t.getCompletedAt()));
        map.put("failureReason", t.getFailureReason() != null ? t.getFailureReason() : "");
        map.put("resultSummary", parseResultSummary(t.getResultSummary()));
        return map;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseResultSummary(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (IOException e) {
            return Map.of();
        }
    }

    private String formatDt(LocalDateTime dt) {
        return dt != null ? dt.format(DT_FMT) : null;
    }
}
