package com.xiyu.bid.resources.service;

import com.xiyu.bid.resources.dto.MarginDTO;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/** Margin ledger Excel export service. */
@Service
@RequiredArgsConstructor
public class MarginExportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String[] HEADERS = {
            "项目名称", "业主单位", "项目负责人", "投标负责人",
            "缴纳金额", "缴纳日期", "缴纳方式",
            "收款方名称", "收款方账号",
            "应退日期", "退回金额", "转服务费金额", "实际退回日期", "状态"
    };

    private final MarginService marginService;

    /**
     * Generate margin ledger Excel bytes.
     *
     * @param uid    current user ID
     * @param role   current user role
     * @param filters query filters (same as list endpoint)
     * @return .xlsx file content as byte array
     */
    public byte[] exportToExcel(Long uid, String role, Map<String, String> filters) {
        List<MarginDTO> data = marginService.getList(uid, role, filters, 1, Integer.MAX_VALUE);

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("保证金台账");
            CellStyle headerStyle = createHeaderStyle(wb);
            CellStyle moneyStyle = createMoneyStyle(wb);

            writeHeader(sheet, headerStyle);
            writeDataRows(sheet, data, moneyStyle);
            autoSizeColumns(sheet);

            return toBytes(wb);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate Excel file", e);
        }
    }

    private CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle createMoneyStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        DataFormat fmt = wb.createDataFormat();
        style.setDataFormat(fmt.getFormat("#,##0.00"));
        return style;
    }

    private void writeHeader(Sheet sheet, CellStyle style) {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < HEADERS.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(HEADERS[i]);
            cell.setCellStyle(style);
        }
    }

    private void writeDataRows(Sheet sheet, List<MarginDTO> data, CellStyle moneyStyle) {
        for (int i = 0; i < data.size(); i++) {
            MarginDTO dto = data.get(i);
            Row row = sheet.createRow(i + 1);
            int col = 0;
            row.createCell(col++).setCellValue(nullSafe(dto.getProjectName()));
            row.createCell(col++).setCellValue(nullSafe(dto.getOwnerUnit()));
            row.createCell(col++).setCellValue(nullSafe(dto.getProjectLeaderName()));
            row.createCell(col++).setCellValue(nullSafe(dto.getBiddingLeaderName()));

            Cell amountCell = row.createCell(col++);
            setDecimalCell(amountCell, dto.getDepositAmount(), moneyStyle);

            row.createCell(col++).setCellValue(formatDate(dto.getPaymentDate()));
            row.createCell(col++).setCellValue(nullSafe(dto.getDepositPaymentMethod()));
            row.createCell(col++).setCellValue(nullSafe(dto.getPayeeName()));
            row.createCell(col++).setCellValue(nullSafe(dto.getPayeeAccount()));
            row.createCell(col++).setCellValue(formatDate(dto.getExpectedReturnDate()));

            Cell returnedCell = row.createCell(col++);
            setDecimalCell(returnedCell, dto.getReturnedAmount(), moneyStyle);

            Cell serviceFeeCell = row.createCell(col++);
            setDecimalCell(serviceFeeCell, dto.getServiceFeeAmount(), moneyStyle);

            row.createCell(col++).setCellValue(formatDate(dto.getActualReturnDate()));
            row.createCell(col).setCellValue(nullSafe(dto.getStatusLabel()));
        }
    }

    private void setDecimalCell(Cell cell, BigDecimal value, CellStyle style) {
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        }
        cell.setCellStyle(style);
    }

    private String formatDate(LocalDateTime dt) {
        return dt != null ? dt.format(DATE_FMT) : "";
    }

    private String nullSafe(String s) {
        return s != null ? s : "";
    }

    private void autoSizeColumns(Sheet sheet) {
        for (int i = 0; i < HEADERS.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private byte[] toBytes(Workbook wb) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        return out.toByteArray();
    }
}
