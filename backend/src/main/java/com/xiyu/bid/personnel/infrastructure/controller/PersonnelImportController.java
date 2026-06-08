package com.xiyu.bid.personnel.infrastructure.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.personnel.application.service.ImportPersonnelAppService;
import com.xiyu.bid.personnel.application.service.ImportPersonnelAppService.ImportProgressInfo;
import com.xiyu.bid.personnel.domain.model.importtask.PersonnelImportTask;
import com.xiyu.bid.personnel.infrastructure.excel.PersonnelImportTemplateGenerator;
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

@RestController
@RequestMapping("/api/knowledge/personnel")
@RequiredArgsConstructor
@Slf4j
public class PersonnelImportController {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    private final ImportPersonnelAppService importAppService;
    private final PersonnelImportTemplateGenerator templateGenerator;

    @PostMapping("/import")
    @PreAuthorize("hasAnyAuthority('bid_admin', 'bid_lead')")
    public ResponseEntity<ApiResponse<ImportTaskResponse>> startImport(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) {

        validateFile(file);

        Long currentUserId = extractUserId(userDetails);
        String operatorName = resolveOperatorName(userDetails);
        PersonnelImportTask task = importAppService.initiateImportTask(currentUserId, operatorName);

        importAppService.executeImportAsync(task.id(), file, currentUserId);

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
    @PreAuthorize("hasAnyAuthority('bid_admin', 'bid_lead')")
    public ResponseEntity<ApiResponse<ImportProgressInfo>> getImportProgress(@PathVariable Long taskId) {
        ImportProgressInfo progress = importAppService.getProgress(taskId);
        return ResponseEntity.ok(ApiResponse.success("获取进度成功", progress));
    }

    @GetMapping("/import/{taskId}/report")
    @PreAuthorize("hasAnyAuthority('bid_admin', 'bid_lead')")
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
    @PreAuthorize("hasAnyAuthority('bid_admin', 'bid_lead', 'bid_specialist')")
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
