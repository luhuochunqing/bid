package com.xiyu.bid.personnel.infrastructure.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.personnel.application.command.PersonnelListCriteria;
import com.xiyu.bid.personnel.application.service.ExportPersonnelAppService;
import com.xiyu.bid.personnel.application.service.ExportPersonnelAppService.ExportProgress;
import com.xiyu.bid.personnel.application.service.ExportPersonnelAppService.ExportTaskInfo;
import com.xiyu.bid.personnel.domain.valueobject.PersonnelStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/knowledge/personnel")
@RequiredArgsConstructor
@Slf4j
public class PersonnelExportController {

    private final ExportPersonnelAppService exportAppService;

    @PostMapping("/export")
    @PreAuthorize("hasAnyAuthority('bid_admin', 'bid_lead', 'bid_specialist')")
    public ResponseEntity<ApiResponse<ExportTaskResponse>> startExport(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String departmentCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String certificateType,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) List<String> highestEducations,
            @RequestParam(required = false) List<String> studyForms,
            @RequestParam(required = false) String majorKeyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate entryDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate entryDateTo,
            @RequestParam(required = false) String certificateKeyword,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long currentUserId = extractUserId(userDetails);
        String operatorName = resolveOperatorName(userDetails);

        PersonnelListCriteria criteria = PersonnelListCriteria.ofFull(
                keyword, departmentCode,
                status != null ? PersonnelStatus.valueOf(status.toUpperCase()) : null,
                certificateType,
                gender, highestEducations, studyForms, majorKeyword,
                entryDateFrom, entryDateTo, certificateKeyword, null, null
        );

        ExportTaskInfo taskInfo = exportAppService.initiateExportTask(currentUserId, operatorName);
        exportAppService.executeExportAsync(taskInfo.taskId(), criteria, currentUserId);

        ExportTaskResponse response = new ExportTaskResponse(
                taskInfo.taskId(),
                taskInfo.taskNo(),
                "PROCESSING",
                "导出任务已创建，正在处理中..."
        );

        return ResponseEntity.accepted()
                .body(ApiResponse.success("导出任务已创建", response));
    }

    @GetMapping("/export/{taskId}")
    @PreAuthorize("hasAnyAuthority('bid_admin', 'bid_lead', 'bid_specialist')")
    public ResponseEntity<ApiResponse<ExportProgress>> getExportProgress(@PathVariable String taskId) {
        ExportProgress progress = exportAppService.getProgress(taskId);
        return ResponseEntity.ok(ApiResponse.success("获取进度成功", progress));
    }

    @GetMapping("/export/{taskId}/download")
    @PreAuthorize("hasAnyAuthority('bid_admin', 'bid_lead', 'bid_specialist')")
    public ResponseEntity<Resource> downloadExportFile(@PathVariable String taskId) {
        try {
            byte[] zipBytes = exportAppService.getExportFile(taskId);
            ByteArrayResource resource = new ByteArrayResource(zipBytes);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"personnel_export_" + taskId + ".zip\"")
                    .contentType(MediaType.parseMediaType("application/zip"))
                    .contentLength(zipBytes.length)
                    .body(resource);

        } catch (IllegalArgumentException e) {
            log.warn("导出文件不存在: taskId={}", taskId);
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            log.error("下载导出文件失败", e);
            return ResponseEntity.internalServerError().build();
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

    public record ExportTaskResponse(
            String taskId,
            String taskNo,
            String status,
            String message
    ) {}
}
