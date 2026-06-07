// checkstyle:off
package com.xiyu.bid.performance.infrastructure;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.performance.application.command.PerformanceUpsertCommand;
import com.xiyu.bid.performance.application.dto.PerformanceDTO;
import com.xiyu.bid.performance.application.service.CreatePerformanceAppService;
import com.xiyu.bid.performance.application.service.UpdatePerformanceAppService;
import com.xiyu.bid.performance.application.service.DeletePerformanceAppService;
import com.xiyu.bid.performance.application.service.ListPerformanceAppService;
import com.xiyu.bid.performance.application.service.PerformanceImportExportService;
import com.xiyu.bid.performance.application.service.PerformanceImportResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/knowledge/performance")
@RequiredArgsConstructor
public class PerformanceController {

    private final CreatePerformanceAppService createService;
    private final UpdatePerformanceAppService updateService;
    private final DeletePerformanceAppService deleteService;
    private final ListPerformanceAppService listService;
    private final PerformanceImportExportService importExportService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Auditable(action = "CREATE", entityType = "Performance", description = "创建业绩")
    public ResponseEntity<ApiResponse<PerformanceDTO>> create(@Valid @RequestBody PerformanceUpsertCommand command) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("业绩创建成功", createService.create(command)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "READ", entityType = "Performance", description = "获取业绩列表")
    public ResponseEntity<ApiResponse<List<PerformanceDTO>>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) List<String> customerTypes,
            @RequestParam(required = false) List<String> projectTypes,
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(required = false) List<String> customerLevels,
            @RequestParam(required = false) String territory,
            @RequestParam(required = false) String signingDateStart,
            @RequestParam(required = false) String signingDateEnd,
            @RequestParam(required = false) String expiryDateStart,
            @RequestParam(required = false) String expiryDateEnd,
            @RequestParam(required = false) Boolean hasBidNotice,
            @RequestParam(required = false) String projectManagerKeyword
    ) {
        LocalDate signingStart = parseDate(signingDateStart);
        LocalDate signingEnd = parseDate(signingDateEnd);
        LocalDate expiryStart = parseDate(expiryDateStart);
        LocalDate expiryEnd = parseDate(expiryDateEnd);
        var criteria = com.xiyu.bid.performance.application.command.PerformanceSearchCriteria.of(
                keyword, customerTypes, projectTypes, statuses, customerLevels,
                territory, signingStart, signingEnd, expiryStart, expiryEnd,
                hasBidNotice, projectManagerKeyword);
        return ResponseEntity.ok(ApiResponse.success("业绩列表获取成功", listService.list(criteria)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "READ", entityType = "Performance", description = "获取业绩详情")
    public ResponseEntity<ApiResponse<PerformanceDTO>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("业绩详情获取成功", listService.get(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Auditable(action = "UPDATE", entityType = "Performance", description = "更新业绩")
    public ResponseEntity<ApiResponse<PerformanceDTO>> update(@PathVariable Long id,
            @Valid @RequestBody PerformanceUpsertCommand command) {
        return ResponseEntity.ok(ApiResponse.success("业绩更新成功", updateService.update(id, command)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Auditable(action = "DELETE", entityType = "Performance", description = "删除业绩")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        deleteService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // -- import / export --

    @GetMapping("/template")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<byte[]> downloadTemplate() throws IOException {
        byte[] data = importExportService.generateTemplate();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=performance_template.xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(data);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Auditable(action = "IMPORT", entityType = "Performance", description = "批量导入业绩")
    public ResponseEntity<ApiResponse<PerformanceImportResult>> batchImport(
            @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(ApiResponse.success("导入完成", importExportService.batchImport(file)));
    }

    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Auditable(action = "EXPORT", entityType = "Performance", description = "批量导出业绩")
    public ResponseEntity<byte[]> batchExport(
            @RequestParam(required = false) List<Long> ids) throws IOException {
        byte[] data = importExportService.batchExport(ids);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=performance_export.xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(data);
    }

    @GetMapping("/export-zip")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Auditable(action = "EXPORT", entityType = "Performance", description = "ZIP导出业绩含附件")
    public ResponseEntity<byte[]> batchExportZip(
            @RequestParam(required = false) List<Long> ids) throws IOException {
        byte[] data = importExportService.batchExportZip(ids);
        String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"业绩台账_" + timestamp + ".zip\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(data);
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDate.parse(s); }
        catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException("日期格式错误，应为 YYYY-MM-DD: " + s);
        }
    }
}
