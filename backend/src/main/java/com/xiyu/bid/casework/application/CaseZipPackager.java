package com.xiyu.bid.casework.application;

import com.xiyu.bid.casework.domain.model.CaseExportZipEntry;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
@Slf4j
public final class CaseZipPackager {

    private static final String INDEX_SHEET_NAME = "案例索引";

    private static final int BUFFER_SIZE = 64 * 1024;

    public byte[] buildCaseZipBytes(
            final List<CaseExportZipEntry> entries,
            final byte[] indexExcelBytes) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(BUFFER_SIZE);
        try (ZipOutputStream zipOut = new ZipOutputStream(buffer)) {
            if (indexExcelBytes != null && indexExcelBytes.length > 0) {
                ZipEntry indexEntry = new ZipEntry("_案例索引.xlsx");
                zipOut.putNextEntry(indexEntry);
                zipOut.write(indexExcelBytes);
                zipOut.closeEntry();
            }

            for (CaseExportZipEntry entry : entries) {
                ZipEntry zipEntry = new ZipEntry(entry.entryPath());
                zipOut.putNextEntry(zipEntry);
                zipOut.write(entry.content());
                zipOut.closeEntry();
            }

            zipOut.finish();
            return buffer.toByteArray();
        } catch (IOException e) {
            log.error("Failed to build case ZIP bytes", e);
            throw new IllegalStateException(
                    "Failed to package case files into ZIP", e);
        }
    }

    public byte[] buildCaseIndexExcel(final List<CaseIndexRow> indexRows) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet(INDEX_SHEET_NAME);
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);

            createHeaderRow(sheet, headerStyle);

            int rowNum = 1;
            for (CaseIndexRow row : indexRows) {
                Row dataRow = sheet.createRow(rowNum++);
                fillDataRow(dataRow, row, dataStyle);
            }

            for (int i = 0; i < 11; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            log.error("Failed to build case index Excel", e);
            throw new IllegalStateException(
                    "Failed to generate case index Excel", e);
        }
    }

    private CellStyle createHeaderStyle(final Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        style.setFont(headerFont);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle createDataStyle(final Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private void createHeaderRow(final Sheet sheet, final CellStyle headerStyle) {
        Row headerRow = sheet.createRow(0);
        String[] headers = {
                "案例ID", "来源项目名称", "评分项标题", "评分类别",
                "客户类型", "项目类型", "中标结果", "产品线",
                "复用次数", "状态", "创建时间"
        };

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private void fillDataRow(
            final Row row,
            final CaseIndexRow data,
            final CellStyle dataStyle) {
        row.createCell(0).setCellValue(data.caseId());
        row.createCell(1).setCellValue(data.sourceProjectName());
        row.createCell(2).setCellValue(data.scoringPointTitle());
        row.createCell(3).setCellValue(data.scoringCategory());
        row.createCell(4).setCellValue(data.customerType());
        row.createCell(5).setCellValue(data.projectType());
        row.createCell(6).setCellValue(data.bidResult());
        row.createCell(7).setCellValue(data.productLine());
        row.createCell(8).setCellValue(data.reuseCount());
        row.createCell(9).setCellValue(data.status());
        row.createCell(10).setCellValue(data.createdAt());
    }

    public record CaseIndexRow(
            Long caseId,
            String sourceProjectName,
            String scoringPointTitle,
            String scoringCategory,
            String customerType,
            String projectType,
            String bidResult,
            String productLine,
            int reuseCount,
            String status,
            String createdAt
    ) {
    }
}
