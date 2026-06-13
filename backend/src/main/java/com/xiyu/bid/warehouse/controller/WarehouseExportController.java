package com.xiyu.bid.warehouse.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.warehouse.application.WarehouseExportAppService;
import com.xiyu.bid.warehouse.application.WarehouseLedgerExportAppService;
import com.xiyu.bid.warehouse.domain.WarehouseLedgerExportPolicy.Section;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * 仓库台账导出控制器 — 独立的导出相关端点，避免 WarehouseController 超行。
 */
@RestController
@RequestMapping("/api/knowledge/warehouses/export")
@RequiredArgsConstructor
@Slf4j
public class WarehouseExportController {

    private static final String PERM = RoleProfileCatalog.WAREHOUSE_MANAGE_PERMISSION;
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FILENAME_DT_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final WarehouseExportAppService exportAppService;
    private final WarehouseLedgerExportAppService ledgerExportAppService;
    private final UserResolver userResolver;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @PostMapping
    @PreAuthorize("hasAuthority('" + PERM + "')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> triggerExport(
            @RequestBody(required = false) Map<String, Object> body) {
        Long operatorId = userResolver.resolveCurrentUserId();
        if (operatorId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("未登录"));
        }
        String operatorLabel = userResolver.resolveCurrentOperatorLabel();
        WarehouseExportAppService.ExportTaskResult result;
        if (body != null && body.get("ids") instanceof List<?> rawIds) {
            List<Long> ids = rawIds.stream()
                    .filter(o -> o instanceof Number)
                    .map(o -> ((Number) o).longValue())
                    .toList();
            if (ids.isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("未选择任何仓库"));
            }
            result = exportAppService.exportByIds(ids, operatorId, operatorLabel);
        } else {
            WarehouseFilterDTO filterDTO = body == null ? null
                    : objectMapper.convertValue(body, WarehouseFilterDTO.class);
            result = exportAppService.export(filterDTO, operatorId, operatorLabel);
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("导出任务已创建", Map.of("taskId", result.taskId())));
    }

    @PostMapping("/ledger")
    @PreAuthorize("hasAuthority('" + PERM + "')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> triggerLedgerExport(
            @RequestBody(required = false) Map<String, Object> body) {
        Long operatorId = userResolver.resolveCurrentUserId();
        if (operatorId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("未登录"));
        }
        String operatorLabel = userResolver.resolveCurrentOperatorLabel();

        String scope = body != null && body.get("scope") instanceof String s ? s : "filter";
        Set<Section> sections = parseSections(body);
        if (sections.isEmpty()) sections = Set.of(Section.BASIC, Section.LEASE, Section.DOC);

        WarehouseFilterDTO filterDTO = null;
        List<Long> ids = null;

        if ("ids".equals(scope)) {
            if (!(body != null && body.get("ids") instanceof List<?> rawIds)) {
                return ResponseEntity.badRequest().body(ApiResponse.error("勾选模式需要 ids 字段"));
            }
            ids = rawIds.stream().filter(o -> o instanceof Number).map(o -> ((Number) o).longValue()).toList();
            if (ids.isEmpty()) return ResponseEntity.badRequest().body(ApiResponse.error("未选择任何仓库"));
        } else if (!"all_in_use".equals(scope)) {
            // filter
            filterDTO = body == null ? null : objectMapper.convertValue(body, WarehouseFilterDTO.class);
        }

        WarehouseLedgerExportAppService.ExportRequest req = new WarehouseLedgerExportAppService.ExportRequest(
                scope, ids, filterDTO, sections);
        WarehouseExportAppService.ExportTaskResult result = ledgerExportAppService.trigger(req, operatorId, operatorLabel);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("台账导出任务已创建", Map.of("taskId", result.taskId())));
    }

    @SuppressWarnings("unchecked")
    private Set<Section> parseSections(Map<String, Object> body) {
        if (body == null) return Set.of();
        Object v = body.get("sections");
        if (!(v instanceof List<?> rawList)) return Set.of();
        Set<Section> out = EnumSet.noneOf(Section.class);
        for (Object o : rawList) {
            if (o instanceof String s) {
                try { out.add(Section.valueOf(s.toUpperCase())); } catch (IllegalArgumentException ignored) { log.debug("Invalid enum value", ignored); }
            }
        }
        return out;
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
            WarehouseExportTaskEntity task = exportAppService.getTaskStatus(taskId, userId);
            String filename = buildDownloadFilename(task);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType("application/zip"))
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

    private String buildDownloadFilename(WarehouseExportTaskEntity task) {
        String ts = task.getCompletedAt() != null
                ? task.getCompletedAt().format(FILENAME_DT_FMT)
                : LocalDateTime.now().format(FILENAME_DT_FMT);
        return "仓库信息导出包_" + ts + ".zip";
    }

    private Map<String, Object> toTaskMap(WarehouseExportTaskEntity t) {
        Map<String, Object> map = new java.util.HashMap<>();
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
