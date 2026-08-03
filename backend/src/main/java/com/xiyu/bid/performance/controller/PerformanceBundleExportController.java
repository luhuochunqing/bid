package com.xiyu.bid.performance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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
        if (request != null && request.ids() != null && !request.ids().isEmpty()) {
            result = exportAppService.exportByIds(request.ids(), attachmentTypes, operatorId);
        } else {
            PerformanceSearchCriteria criteria = (request != null && request.criteria() != null)
                    ? request.criteria() : PerformanceSearchCriteria.empty();
            result = exportAppService.export(criteria, attachmentTypes, operatorId);
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
        Page<PerformanceExportTaskEntity> tasks = exportAppService.listTasks(
                userId, PageRequest.of(page, size, Sort.by("createdAt").descending()));
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
            Path filePath = exportAppService.getExportFile(taskId, userId);
            PerformanceExportTaskEntity task = exportAppService.getTaskStatus(taskId, userId);
            String filename = buildDownloadFilename(task);
            long fileSize = Files.size(filePath);
            StreamingResponseBody body = out -> {
                try (InputStream in = Files.newInputStream(filePath)) {
                    in.transferTo(out);
                }
            };
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
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
