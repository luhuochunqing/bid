package com.xiyu.bid.personnel.infrastructure.excel;

import com.xiyu.bid.personnel.domain.importvalidation.ParsedCertificateRow;
import com.xiyu.bid.personnel.domain.importvalidation.ParsedEducationRow;
import com.xiyu.bid.personnel.domain.importvalidation.ParsedPersonnelRow;
import com.xiyu.bid.personnel.domain.importvalidation.PersonnelImportEnumMapping;
import com.xiyu.bid.personnel.domain.importvalidation.PersonnelImportValidator;
import com.xiyu.bid.personnel.domain.importvalidation.ValidationResult;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Component
public class PersonnelExcelImporter {

    private static final String SHEET_BASIC_INFO = "基础信息";
    private static final String SHEET_EDUCATION = "教育经历";
    private static final String SHEET_CERTIFICATES = "证书与职称";

    public ImportResult importFromStream(InputStream inputStream) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            validateSheetCount(workbook);

            Sheet basicInfoSheet = workbook.getSheet(SHEET_BASIC_INFO);
            Sheet educationSheet = workbook.getSheet(SHEET_EDUCATION);
            Sheet certificatesSheet = workbook.getSheet(SHEET_CERTIFICATES);

            List<ParsedPersonnelRow> personnelRows = parseBasicInfoRows(basicInfoSheet);
            List<ParsedEducationRow> educationRows = parseEducationRows(educationSheet);
            List<ParsedCertificateRow> certificateRows = parseCertificateRows(certificatesSheet);

            ValidationResult validationResult = PersonnelImportValidator.validate(
                    personnelRows, educationRows, certificateRows);

            return new ImportResult(personnelRows, educationRows, certificateRows, validationResult);
        }
    }

    private void validateSheetCount(Workbook workbook) {
        if (workbook.getNumberOfSheets() < 3) {
            throw new IllegalArgumentException("Excel 文件必须包含至少 3 个 Sheet：" +
                    SHEET_BASIC_INFO + "、" + SHEET_EDUCATION + "、" + SHEET_CERTIFICATES);
        }
    }

    private List<ParsedPersonnelRow> parseBasicInfoRows(Sheet sheet) {
        List<ParsedPersonnelRow> rows = new ArrayList<>();
        int lastRow = sheet.getLastRowNum();
        for (int i = 1; i <= lastRow; i++) {
            Row row = sheet.getRow(i);
            if (isEmptyRow(row)) continue;
            rows.add(mapBasicInfoRow(row, i + 1));
        }
        return rows;
    }

    private List<ParsedEducationRow> parseEducationRows(Sheet sheet) {
        List<ParsedEducationRow> rows = new ArrayList<>();
        int lastRow = sheet.getLastRowNum();
        for (int i = 1; i <= lastRow; i++) {
            Row row = sheet.getRow(i);
            if (isEmptyRow(row)) continue;
            rows.add(mapEducationRow(row, i + 1));
        }
        return rows;
    }

    private List<ParsedCertificateRow> parseCertificateRows(Sheet sheet) {
        List<ParsedCertificateRow> rows = new ArrayList<>();
        int lastRow = sheet.getLastRowNum();
        for (int i = 1; i <= lastRow; i++) {
            Row row = sheet.getRow(i);
            if (isEmptyRow(row)) continue;
            rows.add(mapCertificateRow(row, i + 1));
        }
        return rows;
    }

    private boolean isEmptyRow(Row row) {
        if (row == null) return true;
        for (Cell cell : row) {
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String value = getCellStringValue(cell);
                if (value != null && !value.isBlank()) {
                    return false;
                }
            }
        }
        return true;
    }

    private ParsedPersonnelRow mapBasicInfoRow(Row row, int excelRowNum) {
        return new ParsedPersonnelRow(
                excelRowNum,
                getCellStringValue(row.getCell(0)),
                getCellStringValue(row.getCell(1)),
                getCellStringValue(row.getCell(2)),
                getCellDateValue(row.getCell(3)),
                getCellDateValue(row.getCell(4)),
                getCellStringValue(row.getCell(5)),
                getCellStringValue(row.getCell(6)),
                getCellStringValue(row.getCell(7)),
                getCellStringValue(row.getCell(8)),
                getCellStringValue(row.getCell(9))
        );
    }

    private ParsedEducationRow mapEducationRow(Row row, int excelRowNum) {
        return new ParsedEducationRow(
                excelRowNum,
                getCellStringValue(row.getCell(0)),
                getCellStringValue(row.getCell(1)),
                getCellStringValue(row.getCell(2)),
                getCellDateValue(row.getCell(3)),
                getCellDateValue(row.getCell(4)),
                getCellStringValue(row.getCell(5)),
                getCellStringValue(row.getCell(6)),
                getCellStringValue(row.getCell(7)),
                parseBoolCell(row.getCell(8)),
                // CO-419: 保留原始字符串供校验器校验枚举合法性
                getCellStringValue(row.getCell(8))
        );
    }

    private ParsedCertificateRow mapCertificateRow(Row row, int excelRowNum) {
        return new ParsedCertificateRow(
                excelRowNum,
                getCellStringValue(row.getCell(0)),
                getCellStringValue(row.getCell(1)),
                getCellStringValue(row.getCell(2)),
                getCellStringValue(row.getCell(3)),
                // CO-419: 证书类型中文→英文映射（建造师→CONSTRUCTOR 等）
                mapCertificateType(getCellStringValue(row.getCell(4))),
                getCellDateValue(row.getCell(5)),
                getCellDateValue(row.getCell(6)),
                getCellStringValue(row.getCell(7)),
                getCellStringValue(row.getCell(8)),
                parseBoolCell(row.getCell(9)),
                // CO-419: 保留原始字符串供校验器校验枚举合法性
                getCellStringValue(row.getCell(9)),
                getCellStringValue(row.getCell(10))
        );
    }

    /**
     * CO-419: 证书类型中文→英文映射。
     * - 中文值（建造师等）→ 英文枚举（CONSTRUCTOR 等）存储，与前端表单一致
     * - 英文值（CONSTRUCTOR 等）→ 直接保留（向后兼容）
     * - 未识别值 → 原样返回（由校验器报错）
     */
    private String mapCertificateType(String rawType) {
        if (rawType == null || rawType.isBlank()) return rawType;
        String mapped = PersonnelImportEnumMapping.CERT_TYPE_CN_TO_EN.get(rawType);
        return mapped != null ? mapped : rawType;
    }

    private Boolean parseBoolCell(Cell cell) {
        String val = getCellStringValue(cell);
        if (val == null || val.isBlank()) return null;
        return "是".equals(val) || "true".equalsIgnoreCase(val) || "1".equals(val);
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                yield String.valueOf((long) cell.getNumericCellValue());
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
    }

    private java.time.LocalDate getCellDateValue(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        String strVal = getCellStringValue(cell);
        if (strVal != null && !strVal.isBlank()) {
            // CO-419: 兼容 YYYY-MM 月份精度（与表单 type="month" 一致），补日为 1 号
            if (strVal.length() == 7 && strVal.charAt(4) == '-') {
                try {
                    return java.time.YearMonth.parse(strVal).atDay(1);
                } catch (java.time.format.DateTimeParseException e) {
                    return null;
                }
            }
            try {
                return java.time.LocalDate.parse(strVal);
            } catch (java.time.format.DateTimeParseException e) {
                return null;
            }
        }
        return null;
    }

    public record ImportResult(
            List<ParsedPersonnelRow> personnelRows,
            List<ParsedEducationRow> educationRows,
            List<ParsedCertificateRow> certificateRows,
            ValidationResult validationResult
    ) {
        public boolean hasBlockingErrors() {
            return validationResult != null && validationResult.hasBlockingErrors();
        }

        public int totalRows() {
            return personnelRows.size() + educationRows.size() + certificateRows.size();
        }
    }
}
