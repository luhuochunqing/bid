package com.xiyu.bid.performance.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

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

    /** 生成导入模板 Excel */
    public byte[] generateTemplate() throws IOException {
        return templateGenerator.generate();
    }

    /** 批量导入（同步校验，返回结果报告） */
    public PerformanceImportResult batchImport(MultipartFile file) throws IOException {
        var result = new PerformanceImportResult();
        try (InputStream is = file.getInputStream(); var wb = new XSSFWorkbook(is)) {
            var sheet = wb.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                var row = sheet.getRow(i);
                if (row == null) continue;
                try {
                    rowImporter.importRow(row, i + 1);
                    result.successCount++;
                } catch (RuntimeException e) {
                    result.failures.add(new PerformanceImportResult.ImportFailure(i + 1, getCellStr(row, 0), e.getMessage()));
                    result.failureCount++;
                }
            }
        }
        return result;
    }

    /** 批量导出（生成含系统字段的 Excel） */
    public byte[] batchExport(java.util.List<Long> ids) throws IOException {
        return exporter.export(ids);
    }

    /** ZIP 导出（含 Excel 台账 + 附件） */
    public byte[] batchExportZip(java.util.List<Long> ids) throws IOException {
        return zipExporter.exportZip(ids);
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
