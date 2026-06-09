package com.xiyu.bid.warehouse.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.warehouse.application.WarehouseExportAppService;
import com.xiyu.bid.warehouse.dto.WarehouseFilterDTO;
import com.xiyu.bid.warehouse.infrastructure.WarehouseExportTaskEntity;
import lombok.RequiredArgsConstructor;
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

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 仓库台账导出控制器 — 独立的导出相关端点，避免 WarehouseController 超行。
 */
@RestController
@RequestMapping("/api/knowledge/warehouses/export")
@RequiredArgsConstructor
public class WarehouseExportController {

    private static final String PERM = RoleProfileCatalog.WAREHOUSE_MANAGE_PERMISSION;
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final WarehouseExportAppService exportAppService;
    private final UserResolver userResolver;

    @PostMapping
    @PreAuthorize("hasAuthority('" + PERM + "')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> triggerExport(
            @RequestBody(required = false) WarehouseFilterDTO filterDTO) {
        Long operatorId = userResolver.resolveCurrentUserId();
        if (operatorId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("未登录"));
        }
        WarehouseExportAppService.ExportTaskResult result = exportAppService.export(filterDTO, operatorId);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("导出任务已创建", Map.of("taskId", result.taskId())));
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
        Page<WarehouseExportTaskEntity> tasks = exportAppService.listTasks(
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
            WarehouseExportTaskEntity task = exportAppService.getTaskStatus(taskId, userId);
            return ResponseEntity.ok(ApiResponse.success(toTaskMap(task)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/tasks/{taskId}/download")
    @PreAuthorize("hasAuthority('" + PERM + "')")
    public ResponseEntity<byte[]> downloadExportFile(@PathVariable Long taskId) {
        Long userId = userResolver.resolveCurrentUserId();
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try {
            byte[] bytes = exportAppService.getExportFile(taskId, userId);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"warehouse_export.xlsx\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(bytes.length)
                    .body(bytes);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private Map<String, Object> toTaskMap(WarehouseExportTaskEntity t) {
        return Map.of(
                "id", t.getId(),
                "status", t.getStatus().name(),
                "totalCount", t.getTotalCount() != null ? t.getTotalCount() : 0,
                "downloadUrl", t.getDownloadUrl() != null ? t.getDownloadUrl() : "",
                "expiresAt", formatDt(t.getExpiresAt()),
                "createdAt", formatDt(t.getCreatedAt()),
                "completedAt", formatDt(t.getCompletedAt()),
                "failureReason", t.getFailureReason() != null ? t.getFailureReason() : ""
        );
    }

    private String formatDt(LocalDateTime dt) {
        return dt != null ? dt.format(DT_FMT) : null;
    }
}
