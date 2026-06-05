// Input: export repositories, DTOs, and support services
// Output: Excel Export business service operations and export metadata
// Pos: Service/业务层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.export.service;

import com.xiyu.bid.config.ExportConfig;
import com.xiyu.bid.entity.Case;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Qualification;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.Template;
import com.xiyu.bid.audit.service.IAuditLogService;
import com.xiyu.bid.repository.CaseRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.QualificationRepository;
import com.xiyu.bid.repository.TemplateRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExcelExportService {

    private final TenderRepository tenderRepository;
    private final ProjectRepository projectRepository;
    private final QualificationRepository qualificationRepository;
    private final CaseRepository caseRepository;
    private final TemplateRepository templateRepository;
    private final ExportConfig exportConfig;
    private final IAuditLogService auditLogService;
    private final ProjectAccessScopeService projectAccessScopeService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_ONLY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final ExecutorService exportExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r);
        t.setName("export-worker-" + t.getId());
        t.setDaemon(true);
        return t;
    });

    public long exportToExcel(String dataType, Path filePath, String paramsJson, Long userId) {
        return exportToExcelWithResult(dataType, filePath, paramsJson, userId).fileSize();
    }

    public ExportFileResult exportToExcelWithResult(String dataType, Path filePath, String paramsJson, Long userId) {
        if (dataType == null) {
            throw new IllegalArgumentException("Export type cannot be null");
        }

        validateFilePath(filePath);

        long startTime = System.currentTimeMillis();
        int recordCount = 0;
        long fileSize = 0;

        try {
            byte[] data;
            ExportResult result;

            Callable<ExportResult> exportTask = () -> switch (dataType) {
                case "tenders" -> exportTenders();
                case "projects" -> exportProjects();
                case "qualifications" -> exportQualifications();
                case "cases" -> exportCases();
                case "templates" -> exportTemplates();
                default -> throw new IllegalArgumentException("Unsupported export type: " + dataType);
            };

            Future<ExportResult> future = exportExecutor.submit(exportTask);
            result = future.get(exportConfig.getQueryTimeoutSeconds(), TimeUnit.SECONDS);
            data = result.data();
            recordCount = result.recordCount();

            if (recordCount == 0) {
                throw new IllegalArgumentException("当前条件下无数据可导出");
            }

            if (recordCount >= exportConfig.getMaxRecords() || data.length > exportConfig.getMaxFileSizeBytes()) {
                throw new IllegalArgumentException("数据量过大，请缩小筛选范围后重试");
            }

            java.nio.file.Files.write(filePath, data);
            fileSize = data.length;

            logExport(userId, dataType, recordCount, fileSize, true, null,
                    System.currentTimeMillis() - startTime);

            return new ExportFileResult(fileSize, recordCount);

        } catch (TimeoutException e) {
            String errorMsg = String.format("Export query exceeded timeout limit of %d seconds. " +
                    "Please refine your export filters or contact admin.", exportConfig.getQueryTimeoutSeconds());
            logExport(userId, dataType, recordCount, fileSize, false, errorMsg,
                    System.currentTimeMillis() - startTime);
            throw new RuntimeException(errorMsg, e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            String errorMsg = cause != null ? cause.getMessage() : e.getMessage();
            logExport(userId, dataType, recordCount, fileSize, false, errorMsg,
                    System.currentTimeMillis() - startTime);
            if (cause instanceof IllegalArgumentException) throw (IllegalArgumentException) cause;
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause != null) throw new RuntimeException("Failed to export data to Excel: " + errorMsg, cause);
            throw new RuntimeException("Failed to export data to Excel: " + errorMsg, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logExport(userId, dataType, recordCount, fileSize, false, "Export interrupted",
                    System.currentTimeMillis() - startTime);
            throw new RuntimeException("Export was interrupted", e);
        } catch (IOException e) {
            logExport(userId, dataType, recordCount, fileSize, false, e.getMessage(),
                    System.currentTimeMillis() - startTime);
            throw new RuntimeException("Failed to export data to Excel: " + e.getMessage(), e);
        }
    }

    @Deprecated
    public long exportToExcel(String dataType, Path filePath, String paramsJson) {
        return exportToExcel(dataType, filePath, paramsJson, null);
    }

    public String getExportFileName(String dataType) {
        String baseName = switch (dataType) {
            case "tenders" -> "标讯列表";
            case "projects" -> "投标项目列表";
            case "qualifications" -> "资质列表";
            case "cases" -> "案例列表";
            case "templates" -> "模板列表";
            default -> "导出数据";
        };
        return baseName + "_" + LocalDateTime.now().format(TIMESTAMP_FORMATTER) + ".xlsx";
    }

    private void validateFilePath(Path filePath) {
        String originalPath = filePath.toString();
        if (originalPath.contains("..")) {
            throw new IllegalArgumentException("Invalid file path: path traversal not allowed");
        }
        String normalizedPath = filePath.normalize().toString();
        String tempDir = System.getProperty("java.io.tmpdir");
        if (!normalizedPath.startsWith(tempDir) && !normalizedPath.startsWith("/tmp/")
                && !normalizedPath.startsWith("/var/folders/") && !normalizedPath.startsWith("/tmp")) {
            throw new IllegalArgumentException("Invalid file path: export must be to temp directory");
        }
    }

    private ExportResult exportTenders() throws IOException {
        ExportAccessFilter ac = new ExportAccessFilter(projectRepository, projectAccessScopeService);
        Set<Long> exportableIds = ac.exportableTenderIds();
        return new PagedEntityExporter<>(
                page -> tenderRepository.findAll(page),
                exportConfig.getMaxRecords(),
                new String[]{"ID", "标题", "来源", "预算金额", "截止日期", "状态", "AI评分", "风险等级"},
                (row, t) -> {
                    row.createCell(0).setCellValue(t.getId() != null ? t.getId() : 0);
                    row.createCell(1).setCellValue(safeStr(t.getTitle()));
                    row.createCell(2).setCellValue(safeStr(t.getSource()));
                    row.createCell(3).setCellValue(t.getBudget() != null ? t.getBudget().doubleValue() : 0);
                    row.createCell(4).setCellValue(formatDateTime(t.getDeadline()));
                    row.createCell(5).setCellValue(t.getStatus() != null ? t.getStatus().name() : "");
                    row.createCell(6).setCellValue(t.getAiScore() != null ? t.getAiScore() : 0);
                    row.createCell(7).setCellValue(t.getRiskLevel() != null ? t.getRiskLevel().name() : "");
                },
                (t) -> ac.canExportTender(t, exportableIds)
        ).export("标讯列表");
    }

    private ExportResult exportProjects() throws IOException {
        ExportAccessFilter ac = new ExportAccessFilter(projectRepository, projectAccessScopeService);
        Set<Long> exportableIds = ac.exportableProjectIds();
        return new PagedEntityExporter<>(
                page -> projectRepository.findAll(page),
                exportConfig.getMaxRecords(),
                new String[]{"ID", "项目名称", "关联标讯ID", "状态", "项目经理ID", "开始日期", "结束日期"},
                (row, p) -> {
                    row.createCell(0).setCellValue(p.getId() != null ? p.getId() : 0);
                    row.createCell(1).setCellValue(safeStr(p.getName()));
                    row.createCell(2).setCellValue(p.getTenderId() != null ? p.getTenderId() : 0);
                    row.createCell(3).setCellValue(p.getStatus() != null ? p.getStatus().name() : "");
                    row.createCell(4).setCellValue(p.getManagerId() != null ? p.getManagerId() : 0);
                    row.createCell(5).setCellValue(formatDateTime(p.getStartDate()));
                    row.createCell(6).setCellValue(formatDateTime(p.getEndDate()));
                },
                (p) -> ac.canExportProject(p, exportableIds)
        ).export("项目列表");
    }

    private ExportResult exportQualifications() throws IOException {
        return new PagedEntityExporter<>(
                page -> qualificationRepository.findAll(page),
                exportConfig.getMaxRecords(),
                new String[]{"ID", "资质名称", "类型", "级别", "发证日期", "有效期至", "文件路径"},
                (row, q) -> { row.createCell(0).setCellValue(q.getId() != null ? q.getId() : 0); row.createCell(1).setCellValue(safeStr(q.getName())); row.createCell(2).setCellValue(q.getType() != null ? q.getType().name() : ""); row.createCell(3).setCellValue(q.getLevel() != null ? q.getLevel().name() : ""); row.createCell(4).setCellValue(formatDateOnly(q.getIssueDate())); row.createCell(5).setCellValue(formatDateOnly(q.getExpiryDate())); row.createCell(6).setCellValue(safeStr(q.getFileUrl())); },
                (q) -> true
        ).export("资质列表");
    }

    private ExportResult exportCases() throws IOException {
        return new PagedEntityExporter<>(
                page -> caseRepository.findAll(page),
                exportConfig.getMaxRecords(),
                new String[]{"ID", "标题", "行业", "结果", "金额", "项目日期", "客户名称", "地点", "项目周期"},
                (row, c) -> { row.createCell(0).setCellValue(c.getId() != null ? c.getId() : 0); row.createCell(1).setCellValue(safeStr(c.getTitle())); row.createCell(2).setCellValue(c.getIndustry() != null ? c.getIndustry().name() : ""); row.createCell(3).setCellValue(c.getOutcome() != null ? c.getOutcome().name() : ""); row.createCell(4).setCellValue(c.getAmount() != null ? c.getAmount().doubleValue() : 0); row.createCell(5).setCellValue(formatDateOnly(c.getProjectDate())); row.createCell(6).setCellValue(safeStr(c.getCustomerName())); row.createCell(7).setCellValue(safeStr(c.getLocationName())); row.createCell(8).setCellValue(safeStr(c.getProjectPeriod())); },
                (c) -> true
        ).export("案例列表");
    }

    private ExportResult exportTemplates() throws IOException {
        return new PagedEntityExporter<>(
                page -> templateRepository.findAll(page),
                exportConfig.getMaxRecords(),
                new String[]{"ID", "模板名称", "类别", "文件路径", "描述", "当前版本", "文件大小", "创建者ID"},
                (row, t) -> { row.createCell(0).setCellValue(t.getId() != null ? t.getId() : 0); row.createCell(1).setCellValue(safeStr(t.getName())); row.createCell(2).setCellValue(t.getCategory() != null ? t.getCategory().name() : ""); row.createCell(3).setCellValue(safeStr(t.getFileUrl())); row.createCell(4).setCellValue(safeStr(t.getDescription())); row.createCell(5).setCellValue(safeStr(t.getCurrentVersion())); row.createCell(6).setCellValue(safeStr(t.getFileSize())); row.createCell(7).setCellValue(t.getCreatedBy() != null ? t.getCreatedBy() : 0); },
                (t) -> true
        ).export("模板列表");
    }

    private void logExport(Long userId, String dataType, int recordCount, long fileSize,
                           boolean success, String errorMessage, long duration) {
        if (!exportConfig.isAuditEnabled()) return;
        try {
            auditLogService.log(com.xiyu.bid.audit.service.AuditLogService.AuditLogEntry.builder()
                    .userId(userId != null ? String.valueOf(userId) : "system")
                    .action("EXPORT")
                    .entityType(dataType.toUpperCase(java.util.Locale.ROOT))
                    .entityId(null)
                    .description(String.format("Export %s: %d records, %d bytes", dataType, recordCount, fileSize))
                    .success(success)
                    .errorMessage(errorMessage)
                    .build());
            log.info("Export audit: user={}, type={}, records={}, size={}, success={}, duration={}ms",
                    userId, dataType, recordCount, fileSize, success, duration);
        } catch (RuntimeException e) {
            log.error("Failed to log export operation: {}", e.getMessage(), e);
        }
    }

    private String safeStr(String value) { return value != null ? value : ""; }

    private String formatDateTime(LocalDateTime date) {
        return date != null ? date.format(DATE_FORMATTER) : "";
    }

    private String formatDateOnly(LocalDate date) {
        return date != null ? date.format(DATE_ONLY_FORMATTER) : "";
    }

    public record ExportFileResult(long fileSize, int recordCount) {}
}
