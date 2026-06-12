// Input: qualification export/import HTTP requests
// Output: Excel/Zip files for qualification data
// Pos: Controller/导出导入适配层
// 维护声明: 仅维护协议兼容与参数校验；业务规则下沉到 service.
package com.xiyu.bid.qualification.controller;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.qualification.dto.BatchAttachResultDTO;
import com.xiyu.bid.qualification.service.BatchAttachmentService;
import com.xiyu.bid.qualification.service.QualificationExportService;
import com.xiyu.bid.qualification.service.QualificationWebService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge/qualifications")
@RequiredArgsConstructor
public class QualificationExportController {

    private final QualificationWebService qualificationWebService;
    private final QualificationExportService qualificationExportService;
    private final BatchAttachmentService batchAttachmentService;

    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN_STAFF', 'BID_ADMIN', 'BID_LEAD', 'BID_SPECIALIST')")
    @Auditable(action = "EXPORT", entityType = "Qualification", description = "导出资质证书")
    public void exportQualifications(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=资质证书台账_" +
                java.time.LocalDate.now().toString() + ".xlsx");
        qualificationExportService.exportExcel(keyword, status, response.getOutputStream());
    }

    @GetMapping("/template")
    @PreAuthorize("hasAnyRole('ADMIN_STAFF', 'BID_ADMIN', 'BID_LEAD')")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=资质证书导入模板.xlsx");
        qualificationExportService.generateTemplate(response.getOutputStream());
    }

    @PostMapping("/batch-export")
    @PreAuthorize("hasAnyRole('ADMIN_STAFF', 'BID_ADMIN', 'BID_LEAD', 'BID_SPECIALIST')")
    @Auditable(action = "EXPORT", entityType = "Qualification", description = "批量导出资质台账")
    public ResponseEntity<byte[]> batchExport(@RequestBody Map<String, List<Long>> body) throws IOException {
        List<Long> ids = body.get("ids");
        byte[] data = qualificationExportService.batchExportExcel(ids);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=资质证书台账批量导出_" + java.time.LocalDate.now() + ".xlsx")
                .body(data);
    }

    @PostMapping("/batch-download")
    @PreAuthorize("hasAnyRole('ADMIN_STAFF', 'BID_ADMIN', 'BID_LEAD', 'BID_SPECIALIST')")
    @Auditable(action = "EXPORT", entityType = "Qualification", description = "批量下载资质附件")
    public ResponseEntity<byte[]> batchDownload(@RequestBody Map<String, List<Long>> body) throws IOException {
        List<Long> ids = body.get("ids");
        byte[] data = qualificationExportService.batchExportZip(ids);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=资质附件批量下载_" + java.time.LocalDate.now() + ".zip")
                .body(data);
    }

    @PostMapping("/import")
    @PreAuthorize("hasAnyRole('ADMIN_STAFF', 'BID_ADMIN', 'BID_LEAD')")
    @Auditable(action = "CREATE", entityType = "Qualification", description = "导入资质台账")
    public ResponseEntity<ApiResponse<Map<String, Object>>> importQualifications(@RequestParam("file") MultipartFile file) throws IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String operatorName = auth != null ? auth.getName() : "系统导入";
        var summary = qualificationWebService.importFromExcel(file, operatorName);
        Map<String, Object> result = Map.of(
                "total", summary.total(),
                "success", summary.success(),
                "failed", summary.failed(),
                "errors", summary.results().stream()
                        .filter(r -> !r.isSuccess())
                        .map(r -> Map.of(
                                "row", r.getRowNumber(),
                                "certificateNo", r.getCertificateNo(),
                                "reason", r.getFailureReason()
                        ))
                        .toList()
        );
        return ResponseEntity.ok(ApiResponse.success("Import completed", result));
    }

    @PostMapping("/batch-attach")
    @PreAuthorize("hasAnyRole('ADMIN_STAFF', 'BID_ADMIN', 'BID_LEAD')")
    @Auditable(action = "UPDATE", entityType = "Qualification", description = "批量关联资质附件")
    public ResponseEntity<ApiResponse<BatchAttachResultDTO>> batchAttach(@RequestParam("files") List<MultipartFile> files) {
        BatchAttachResultDTO result = batchAttachmentService.process(files);
        return ResponseEntity.ok(ApiResponse.success("附件关联完成", result));
    }
}
