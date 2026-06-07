package com.xiyu.bid.export.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.function.BiConsumer;

public class PagedEntityExporter<T> {

    private final EntityPageSupplier<T> pageSupplier;
    private final int maxRecords;
    private final String[] headers;
    private final BiConsumer<Row, T> cellPopulator;
    private final ExportAccessControl<T> accessControl;

    @FunctionalInterface
    public interface EntityPageSupplier<T> {
        org.springframework.data.domain.Page<T> get(org.springframework.data.domain.Pageable pageable);
    }

    @FunctionalInterface
    public interface ExportAccessControl<T> {
        boolean canExport(T entity);
    }

    public PagedEntityExporter(EntityPageSupplier<T> pageSupplier, int maxRecords,
                              String[] headers, BiConsumer<Row, T> cellPopulator,
                              ExportAccessControl<T> accessControl) {
        this.pageSupplier = pageSupplier;
        this.maxRecords = maxRecords;
        this.headers = headers;
        this.cellPopulator = cellPopulator;
        this.accessControl = accessControl;
    }

    public ExportResult export(String sheetName) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName);
            writeHeader(sheet, workbook);
            int recordCount = paginateAndWrite(sheet);
            autoSizeColumns(sheet);
            workbook.write(out);
            return new ExportResult(out.toByteArray(), recordCount);
        }
    }

    private void writeHeader(Sheet sheet, Workbook workbook) {
        Row headerRow = sheet.createRow(0);
        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font font = workbook.createFont();
        font.setBold(true);
        headerStyle.setFont(font);
        headerStyle.setBorderTop(BorderStyle.THIN);
        headerStyle.setBorderBottom(BorderStyle.THIN);
        headerStyle.setBorderLeft(BorderStyle.THIN);
        headerStyle.setBorderRight(BorderStyle.THIN);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private int paginateAndWrite(Sheet sheet) {
        int rowNum = 1;
        int recordCount = 0;
        int pageNumber = 0;
        boolean hasMoreData = true;

        while (hasMoreData && recordCount < maxRecords) {
            org.springframework.data.domain.Pageable pageable =
                    org.springframework.data.domain.PageRequest.of(pageNumber, 1000);
            org.springframework.data.domain.Page<T> page = pageSupplier.get(pageable);

            for (T entity : page.getContent()) {
                if (recordCount >= maxRecords) break;
                if (!accessControl.canExport(entity)) continue;
                Row row = sheet.createRow(rowNum++);
                cellPopulator.accept(row, entity);
                recordCount++;
            }

            hasMoreData = page.hasNext();
            pageNumber++;
        }
        return recordCount;
    }

    private void autoSizeColumns(Sheet sheet) {
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
            if (sheet.getColumnWidth(i) < 2000) sheet.setColumnWidth(i, 2000);
            if (sheet.getColumnWidth(i) > 8000) sheet.setColumnWidth(i, 8000);
        }
    }
}
