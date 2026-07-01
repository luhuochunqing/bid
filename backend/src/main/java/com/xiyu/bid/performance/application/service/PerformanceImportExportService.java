package com.xiyu.bid.performance.application.service;

import com.xiyu.bid.performance.application.command.PerformanceSearchCriteria;
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
        List<PerformanceRowImporter.ImportRowResult> importedRows = new ArrayList<>();
        try (InputStream is = file.getInputStream(); var wb = new XSSFWorkbook(is)) {
            var sheet = wb.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                var row = sheet.getRow(i);
                if (row == null) continue;
                try {
                    var rowResult = rowImporter.importRow(row, i + 1);
                    importedRows.add(rowResult);
                    result.successCount++;
                } catch (RuntimeException e) {
                    result.failures.add(new PerformanceImportResult.ImportFailure(i + 1, getCellStr(row, 0), e.getMessage()));
                    result.failureCount++;
                }
            }
        }

        // 附件包归档（即使 Excel 行全部失败也执行，便于用户发现附件问题）
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
                                 PerformanceSearchCriteria criteria) throws IOException {
        return zipExporter.exportZip(ids, criteria);
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
