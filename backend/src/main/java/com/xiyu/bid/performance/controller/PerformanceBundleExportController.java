package com.xiyu.bid.performance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.config.PaginationConstants;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.performance.application.PerformanceBundleExportAppService;
import com.xiyu.bid.performance.application.command.PerformanceSearchCriteria;
import com.xiyu.bid.performance.controller.dto.ExportTaskResponse;
import com.xiyu.bid.performance.infrastructure.persistence.entity.PerformanceExportTaskEntity;
import com.xiyu.bid.warehouse.controller.UserResolver;
import jakarta.validation.Valid;
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
    @Auditable(action = "CREATE", entityType = "PerformanceExportTask", description = "触发业绩合订本导出")
    public ResponseEntity<ApiResponse<Map<String, Object>>> triggerExport(
            @Valid @RequestBody(required = false) BundleExportRequest request) {
        Long operatorId = userResolver.resolveCurrentUserId();
        if (operatorId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("未登录"));
        }
        Set<String> attachmentTypes = request != null ? request.safeAttachmentTypes() : Set.of();

        PerformanceBundleExportAppService.ExportTaskResult result;
        try {
            if (request != null && request.isIdMode()) {
                result = exportAppService.exportByIds(request.ids(), attachmentTypes, operatorId);
            } else {
                PerformanceSearchCriteria criteria = request != null
                        ? request.safeCriteria() : PerformanceSearchCriteria.empty();
                result = exportAppService.export(criteria, attachmentTypes, operatorId);
            }
        } catch (RejectedExecutionException e) {
            log.warn("业绩合订本导出线程池已满，拒绝任务: operatorId={}", operatorId);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("系统繁忙，已有多个导出任务在排队，请稍后重试"));
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("合订本导出任务已创建", Map.of("taskId", result.taskId())));
    }

    @GetMapping("/tasks")
    @PreAuthorize("hasAuthority('" + PERM + "')")
    @Auditable(action = "READ", entityType = "PerformanceExportTask", description = "查询导出任务列表")
    public ResponseEntity<ApiResponse<Map<String, Object>>> listExportTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {
        Long userId = userResolver.resolveCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("未登录"));
        }
        int safeSize = Math.min(Math.max(size, 1), PaginationConstants.MAX_PAGE_SIZE);
        Page<PerformanceExportTaskEntity> tasks = exportAppService.listTasks(
                userId, PageRequest.of(page, safeSize, Sort.by("createdAt").descending()));
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "content", tasks.getContent().stream().map(this::toTaskResponse).toList(),
                "totalElements", tasks.getTotalElements(),
                "totalPages", tasks.getTotalPages(),
                "number", tasks.getNumber(),
                "size", tasks.getSize()
        )));
    }

    @GetMapping("/tasks/{taskId}/status")
    @PreAuthorize("hasAuthority('" + PERM + "')")
    @Auditable(action = "READ", entityType = "PerformanceExportTask", description = "查询导出任务状态")
    public ResponseEntity<ApiResponse<ExportTaskResponse>> getExportTaskStatus(@PathVariable Long taskId) {
        Long userId = userResolver.resolveCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("未登录"));
        }
        try {
            PerformanceExportTaskEntity task = exportAppService.getTaskStatus(taskId, userId);
            return ResponseEntity.ok(ApiResponse.success(toTaskResponse(task)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/tasks/{taskId}/download")
    @PreAuthorize("hasAuthority('" + PERM + "')")
    @Auditable(action = "DOWNLOAD", entityType = "PerformanceExportTask", description = "下载导出文件")
    public ResponseEntity<StreamingResponseBody> downloadExportFile(@PathVariable Long taskId) {
        Long userId = userResolver.resolveCurrentUserId();
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try {
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

    private String buildContentDisposition(String filename) {
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded;
    }

    private ExportTaskResponse toTaskResponse(PerformanceExportTaskEntity t) {
        return ExportTaskResponse.from(t, this::parseResultSummary, DT_FMT);
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
}
