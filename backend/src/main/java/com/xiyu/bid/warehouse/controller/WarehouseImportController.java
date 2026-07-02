package com.xiyu.bid.warehouse.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.warehouse.application.WarehouseImportAppService;
import com.xiyu.bid.warehouse.application.WarehouseImportAttachmentProcessor;
import com.xiyu.bid.warehouse.application.WarehouseImportQueryService;
import com.xiyu.bid.warehouse.application.WarehouseImportTaskStateService;
import com.xiyu.bid.warehouse.infrastructure.WarehouseImportTaskEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseImportTemplateWriter;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 仓库批量导入控制器：模板下载、文件上传、任务状态、错误明细查询。
 */
@RestController
@RequestMapping("/api/knowledge/warehouses/import")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class WarehouseImportController {

    private static final String PERM = RoleProfileCatalog.WAREHOUSE_MANAGE_PERMISSION;
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final WarehouseImportAppService importAppService;
    private final WarehouseImportQueryService importQueryService;
    private final WarehouseImportTemplateWriter templateWriter;
    private final WarehouseImportTaskStateService taskStateService;
    private final UserResolver userResolver;

    @GetMapping("/template")
    @PreAuthorize("hasAuthority('" + PERM + "')")
    public ResponseEntity<byte[]> downloadTemplate() throws IOException {
        byte[] bytes = templateWriter.write();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"warehouse_import_template.xlsx\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(bytes.length)
                .body(bytes);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + PERM + "')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> triggerImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "attachments", required = false) MultipartFile[] attachments) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("请选择要导入的 Excel 文件"));
        }
        User user = userResolver.resolveCurrentUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("未登录"));
        }
        WarehouseImportAppService.ImportTaskResult result = importAppService.triggerImport(
                toBytes(file), toAttachmentInputs(attachments), user);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("导入任务已创建", Map.of("taskId", result.taskId())));
    }

    @GetMapping("/tasks")
    @PreAuthorize("hasAuthority('" + PERM + "')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> listTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {
        User user = userResolver.resolveCurrentUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("未登录"));
        }
        Page<WarehouseImportTaskEntity> tasks = importQueryService.listTasks(
                user.getId(), PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "content", tasks.getContent().stream().map(this::toTaskMap).toList(),
                "totalElements", tasks.getTotalElements(),
                "totalPages", tasks.getTotalPages(),
                "number", tasks.getNumber(),
                "size", tasks.getSize()
        )));
    }

    @GetMapping("/tasks/{taskId}")
    @PreAuthorize("hasAuthority('" + PERM + "')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTask(@PathVariable Long taskId) {
        User user = userResolver.resolveCurrentUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("未登录"));
        }
        try {
            WarehouseImportTaskEntity task = importQueryService.getTask(taskId, user.getId());
            return ResponseEntity.ok(ApiResponse.success(toTaskMap(task)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/tasks/{taskId}/correction")
    @PreAuthorize("hasAuthority('" + PERM + "')")
    public ResponseEntity<byte[]> downloadCorrectionFile(@PathVariable Long taskId) throws IOException {
        User user = userResolver.resolveCurrentUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        byte[] bytes = taskStateService.getCorrectionFile(taskId, user.getId());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"warehouse_import_correction.xlsx\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(bytes.length)
                .body(bytes);
    }

    private Map<String, Object> toTaskMap(WarehouseImportTaskEntity t) {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("id", t.getId());
        map.put("status", t.getStatus().name());
        map.put("totalRows", t.getTotalRows() != null ? t.getTotalRows() : 0);
        map.put("validRows", t.getValidRows() != null ? t.getValidRows() : 0);
        map.put("invalidRows", t.getInvalidRows() != null ? t.getInvalidRows() : 0);
        map.put("importedRows", t.getImportedRows() != null ? t.getImportedRows() : 0);
        map.put("errorDetails", t.getErrorDetails() != null ? t.getErrorDetails() : "");
        map.put("failureReason", t.getFailureReason() != null ? t.getFailureReason() : "");
        map.put("sourceFilename", t.getSourceFilename() != null ? t.getSourceFilename() : "");
        map.put("createdByUsername", t.getCreatedByUsername() != null ? t.getCreatedByUsername() : "");
        map.put("createdAt", formatDt(t.getCreatedAt()));
        map.put("completedAt", formatDt(t.getCompletedAt()));
        parseAttachmentAndCorrectionInfo(t.getErrorDetails(), map);
        return map;
    }

    private void parseAttachmentAndCorrectionInfo(String errorDetails, Map<String, Object> map) {
        if (errorDetails == null || errorDetails.isEmpty()) {
            map.put("attachedCount", 0);
            map.put("unmatchedFiles", List.of());
            map.put("hasCorrectionFile", false);
            map.put("correctionFileUrl", null);
            return;
        }
        int attachedCount = 0;
        List<Map<String, Object>> unmatchedFiles = new ArrayList<>();
        boolean hasCorrectionFile = false;
        String correctionFileUrl = null;
        for (String line : errorDetails.split("\n")) {
            if (line.startsWith("[ATTACH_RESULT]")) {
                String content = line.substring("[ATTACH_RESULT]".length());
                String[] parts = content.split(" ");
                for (String part : parts) {
                    if (part.startsWith("matched=")) {
                        attachedCount = Integer.parseInt(part.substring("matched=".length()));
                    }
                }
            } else if (line.startsWith("[UNMATCHED] ")) {
                String content = line.substring("[UNMATCHED] ".length());
                String[] parts = content.split(" \\| ", 2);
                String filename = parts.length > 0 ? parts[0] : "";
                String reason = parts.length > 1 ? parts[1] : "";
                unmatchedFiles.add(Map.of("filename", filename, "reason", reason));
            } else if (line.startsWith("[CORRECTION_FILE]")) {
                hasCorrectionFile = true;
                String path = line.substring("[CORRECTION_FILE]".length());
                if (path.contains("/")) {
                    String filename = path.substring(path.lastIndexOf("/") + 1);
                    correctionFileUrl = "/api/knowledge/warehouses/import/tasks/" + map.get("id") + "/correction";
                }
            }
        }
        map.put("attachedCount", attachedCount);
        map.put("unmatchedFiles", unmatchedFiles);
        map.put("hasCorrectionFile", hasCorrectionFile);
        map.put("correctionFileUrl", correctionFileUrl);
    }

    private String formatDt(LocalDateTime dt) {
        return dt != null ? dt.format(DT_FMT) : null;
    }

    private static byte[] toBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("读取导入文件失败: " + file.getOriginalFilename(), e);
        }
    }

    private static List<WarehouseImportAttachmentProcessor.AttachmentInput> toAttachmentInputs(MultipartFile[] files) {
        if (files == null) return List.of();
        List<WarehouseImportAttachmentProcessor.AttachmentInput> result = new ArrayList<>(files.length);
        for (MultipartFile mf : files) {
            if (mf == null || mf.isEmpty()) continue;
            result.add(new WarehouseImportAttachmentProcessor.AttachmentInput(
                    mf.getOriginalFilename(), toBytes(mf)));
        }
        return result;
    }
}
