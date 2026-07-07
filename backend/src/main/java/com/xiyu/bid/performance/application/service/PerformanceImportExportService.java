package com.xiyu.bid.performance.application.service;

import com.xiyu.bid.performance.application.command.PerformanceSearchCriteria;
import com.xiyu.bid.performance.application.dto.PerformanceExportCriteria;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * 业绩批量导入导出编排服务（蓝图 4.5）
 */
@Service
@RequiredArgsConstructor
public class PerformanceImportExportService {

    private final PerformanceExcelTemplateGenerator templateGenerator;
    private final PerformanceExcelExporter exporter;
    private final PerformanceZipExporter zipExporter;
    private final PerformanceRowImporter rowImporter;
    private final PerformanceImportAttachmentProcessor attachmentProcessor;

    /** 生成导入模板 Excel */
    public byte[] generateTemplate() throws IOException {
        return templateGenerator.generate();
    }

    /** 批量导入（同步校验，返回结果报告） */
    public PerformanceImportResult batchImport(MultipartFile file,
                                                List<PerformanceImportAttachmentProcessor.AttachmentInput> attachments)
            throws IOException {
        var result = new PerformanceImportResult();
        List<PerformanceRowImporter.ParsedRow> parsedRows = new ArrayList<>();
        try (InputStream is = file.getInputStream(); var wb = new XSSFWorkbook(is)) {
            var sheet = wb.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                var row = sheet.getRow(i);
                if (row == null) continue;
                try {
                    parsedRows.add(rowImporter.parseRow(row, i + 1));
                } catch (RuntimeException e) {
                    result.failures.add(new PerformanceImportResult.ImportFailure(i + 1, getCellStr(row, 0), e.getMessage()));
                    result.failureCount++;
                }
            }
        }

        // 没有可导入的行时直接返回（不执行附件归档）
        if (parsedRows.isEmpty()) {
            return result;
        }

        // 校验 Excel 中声明的附件是否都在附件包中
        var missing = attachmentProcessor.findMissingDeclaredAttachments(parsedRows, attachments);
        if (!missing.isEmpty()) {
            java.util.Map<Integer, java.util.List<String>> reasonsByRow = new java.util.HashMap<>();
            java.util.Map<Integer, String> contractNameByRow = new java.util.HashMap<>();
            for (var m : missing) {
                reasonsByRow.computeIfAbsent(m.rowNum(), k -> new java.util.ArrayList<>()).add(m.fileName());
                contractNameByRow.putIfAbsent(m.rowNum(), m.contractName());
            }
            for (var entry : reasonsByRow.entrySet()) {
                String reason = "附件未上传: " + String.join(", ", entry.getValue());
                result.failures.add(new PerformanceImportResult.ImportFailure(
                        entry.getKey(), contractNameByRow.get(entry.getKey()), reason));
                result.failureCount++;
            }
            return result;
        }

        // 保存业绩记录
        List<PerformanceRowImporter.ImportRowResult> importedRows = new ArrayList<>();
        for (var parsed : parsedRows) {
            importedRows.add(rowImporter.saveParsedRow(parsed));
            result.successCount++;
        }

        // 附件包归档
        if (attachments != null && !attachments.isEmpty() && !importedRows.isEmpty()) {
            var attachResult = attachmentProcessor.attachFiles(importedRows, attachments);
            result.attachedCount = attachResult.matchedCount();
            result.unmatchedFiles = attachResult.unmatched().stream()
                    .map(PerformanceImportAttachmentProcessor.UnmatchedFile::filename)
                    .toList();
        }
        return result;
    }

    /** 批量导出（生成含系统字段的 Excel） */
    public byte[] batchExport(java.util.List<Long> ids,
                              PerformanceSearchCriteria criteria) throws IOException {
        return exporter.export(ids, criteria);
    }

    /** ZIP 导出（含 Excel 台账 + 附件） */
    public byte[] batchExportZip(java.util.List<Long> ids,
                                 PerformanceSearchCriteria criteria,
                                 PerformanceExportCriteria exportCriteria) throws IOException {
        return zipExporter.exportZip(ids, criteria, exportCriteria);
    }

    private String getCellStr(org.apache.poi.ss.usermodel.Row row, int idx) {
        var cell = row.getCell(idx);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            default -> null;
        };
    }
}
