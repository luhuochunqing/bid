package com.xiyu.bid.personnel.infrastructure.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.personnel.application.service.ImportPersonnelAppService;
import com.xiyu.bid.personnel.application.service.ImportPersonnelAppService.ImportProgressInfo;
import com.xiyu.bid.personnel.domain.model.importtask.PersonnelImportTask;
import com.xiyu.bid.personnel.infrastructure.excel.PersonnelImportTemplateGenerator;
import com.xiyu.bid.entity.RoleProfileCatalog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * CO-469 第八轮 P2 根因补修说明（MultipartFile 异步生命周期）：
 *
 * 第八轮 P1（commit 2e4613fbd / da755ce28）已修复根因 2（JSON 序列化 + failImportTask 降级），
 * 但漏掉了根因 1：{@code @Async} 方法直接接收 {@link MultipartFile}。
 *
 * Spring MVC 的 {@link MultipartFile} 实现基于 Servlet 容器（Tomcat）的磁盘临时文件，
 * HTTP 请求一旦结束（Controller 返回后），Tomcat 会立即清理该临时文件。而 {@code @Async}
 * 方法实际执行时往往已是几十毫秒之后，{@code file.getInputStream()} 抛
 * {@link java.nio.file.NoSuchFileException}，异步线程进入失败兜底逻辑。
 *
 * backend.log 铁证（2026-07-06 06:25:12.287）：
 * <pre>
 *   [personnel-imp-exp-1] ERROR c.x.b.p.a.s.ImportPersonnelAppService - 导入任务执行失败: taskId=1
 *   java.nio.file.NoSuchFileException: /private/var/folders/.../upload_xxx.tmp
 * </pre>
 *
 * 修复：在同步阶段（HTTP 请求仍存活）调用 {@link MultipartFile#getBytes()} 把文件内容
 * 完整读到内存 {@code byte[]}，再传给 {@code @Async} 方法。{@code byte[]} 是不可变的
 * 纯 JDK 对象，不依赖 request 生命周期，从根本上消除该问题。
 */
@RestController
@RequestMapping("/api/knowledge/personnel")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("isAuthenticated()")
public class PersonnelImportController {

    // CO-394: 权限点统一为 RoleProfileCatalog 常量，对齐 Warehouse 模板风格
    private static final String MANAGE_PERM = RoleProfileCatalog.PERSONNEL_MANAGE_PERMISSION;

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    private final ImportPersonnelAppService importAppService;
    private final PersonnelImportTemplateGenerator templateGenerator;

    @PostMapping("/import")
    @PreAuthorize("hasAuthority('" + MANAGE_PERM + "')")
    public ResponseEntity<ApiResponse<ImportTaskResponse>> startImport(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) throws IOException {

        validateFile(file);

        // CO-469 第八轮 P2：在同步阶段读完文件内容到 byte[]，避开 Tomcat 清理临时文件
        // 详见类顶部注释。注意必须在 Controller 返回前完成读取。
        byte[] fileBytes = file.getBytes();
        String originalFilename = file.getOriginalFilename();

        Long currentUserId = extractUserId(userDetails);
        String operatorName = resolveOperatorName(userDetails);
        PersonnelImportTask task = importAppService.initiateImportTask(currentUserId, operatorName);

        importAppService.executeImportAsync(task.id(), fileBytes, originalFilename, currentUserId);

        ImportTaskResponse response = new ImportTaskResponse(
                task.id(),
                task.taskNo(),
                task.status().name(),
                "导入任务已创建，正在处理中..."
        );

        return ResponseEntity.accepted()
                .body(ApiResponse.success("导入任务已创建", response));
    }

    @GetMapping("/import/{taskId}")
    @PreAuthorize("hasAuthority('" + MANAGE_PERM + "')")
    public ResponseEntity<ApiResponse<ImportProgressInfo>> getImportProgress(@PathVariable Long taskId) {
        ImportProgressInfo progress = importAppService.getProgress(taskId);
        return ResponseEntity.ok(ApiResponse.success("获取进度成功", progress));
    }

    @GetMapping("/import/{taskId}/report")
    @PreAuthorize("hasAuthority('" + MANAGE_PERM + "')")
    public ResponseEntity<Resource> downloadErrorReport(@PathVariable Long taskId) {
        try {
            byte[] reportBytes = importAppService.getErrorReport(taskId);
            ByteArrayResource resource = new ByteArrayResource(reportBytes);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"import_error_report_" + taskId + ".xlsx\"")
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .contentLength(reportBytes.length)
                    .body(resource);

        } catch (IOException e) {
            log.error("生成错误报告失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/import/template")
    @PreAuthorize("hasAuthority('" + MANAGE_PERM + "')")
    public ResponseEntity<Resource> downloadTemplate() {
        try {
            byte[] templateBytes = templateGenerator.generate();
            ByteArrayResource resource = new ByteArrayResource(templateBytes);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"personnel_import_template.xlsx\"")
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .contentLength(templateBytes.length)
                    .body(resource);

        } catch (IOException e) {
            log.error("生成模板失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".xlsx")) {
            throw new IllegalArgumentException("只支持 .xlsx 格式的 Excel 文件");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("文件大小不能超过 10MB");
        }
    }

    private Long extractUserId(UserDetails userDetails) {
        if (userDetails == null) {
            return 0L;
        }
        try {
            return Long.parseLong(userDetails.getUsername());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private String resolveOperatorName(UserDetails userDetails) {
        return userDetails != null ? userDetails.getUsername() : "system";
    }

    public record ImportTaskResponse(
            Long taskId,
            String taskNo,
            String status,
            String message
    ) {}
}
