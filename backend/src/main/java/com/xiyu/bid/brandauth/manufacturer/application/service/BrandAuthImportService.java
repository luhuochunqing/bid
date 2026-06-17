package com.xiyu.bid.brandauth.manufacturer.application.service;

import com.xiyu.bid.brandauth.manufacturer.application.dto.ManufacturerAuthorizationDTO;
import com.xiyu.bid.brandauth.manufacturer.application.mapper.ManufacturerAuthMapper;
import com.xiyu.bid.brandauth.manufacturer.domain.model.ManufacturerAuthorization;
import com.xiyu.bid.brandauth.manufacturer.domain.port.ManufacturerAuthorizationRepository;
import com.xiyu.bid.brandauth.manufacturer.domain.valueobject.AuthStatus;
import com.xiyu.bid.brandauth.manufacturer.domain.valueobject.ProductLine;
import com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.entity.BrandAuthOperationLogEntity;
import com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.repository.BrandAuthOperationLogJpaRepository;
import com.xiyu.bid.exception.BusinessException;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
/** Batch import brand authorizations from Excel. */
@Service
@RequiredArgsConstructor
public class BrandAuthImportService {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final ManufacturerAuthorizationRepository repository;
    private final BrandAuthOperationLogJpaRepository logRepository;
    private final UserRepository userRepository;
    /**
     * Import brand authorizations from an Excel byte array.
     *
     * @param excelBytes the Excel file bytes (must match the template format)
     * @param userId     the importing user ID
     * @return import result with per-sheet details
     */
    @Transactional
    public ImportResult importExcel(final byte[] excelBytes, final Long userId) {
        ImportResult result = new ImportResult();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                String sheetName = wb.getSheetName(i);
                Sheet sheet = wb.getSheetAt(i);
                if (sheet.getPhysicalNumberOfRows() <= 1) {
                    result.addSheetResult(sheetName, 0, 0, List.of());
                    continue;
                }
                boolean isAgent = "代理商授权".equals(sheetName);
                List<String> errors = new ArrayList<>();
                int successCount = 0;
                int totalDataRows = 0;

                for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
                    Row row = sheet.getRow(rowIdx);
                    if (row == null) continue;
                    totalDataRows++;
                    try {
                        if (isAgent) {
                            importAgentRow(row, userId);
                        } else {
                            importManufacturerRow(row, userId);
                        }
                        successCount++;
                    } catch (RuntimeException e) {
                        errors.add("第" + (rowIdx + 1) + "行: " + e.getMessage());
                    }
                }
                result.addSheetResult(sheetName, totalDataRows, successCount, errors);
            }
        } catch (IOException e) {
            throw new BusinessException("无法读取Excel文件: " + e.getMessage());
        }
        return result;
    }

    private void importManufacturerRow(final Row row, final Long userId) {
        int col = 0;
        String productLineStr = getCellString(row, col++);
        String brandId = getCellString(row, col++);
        String brandName = getCellString(row, col++);
        String importDomestic = getCellString(row, col++);
        String manufacturerName = getCellString(row, col++);
        // col 5: 原厂授权附件文件名 (skip for import - handled separately)
        col++;
        String authStartDateStr = getCellString(row, col++);
        String authEndDateStr = getCellString(row, col++);
        String remarks = getCellString(row, col++);
        // col 9: 补充材料附件文件名 (skip)
        // col++

        ProductLine productLine = parseProductLine(productLineStr);
        LocalDate authStartDate = parseDate(authStartDateStr);
        LocalDate authEndDate = parseDate(authEndDateStr);

        validateRequired(productLineStr, "一级产线");
        validateRequired(brandId, "品牌ID");
        validateRequired(brandName, "品牌");
        validateRequired(importDomestic, "进口/国产");
        validateRequired(manufacturerName, "品牌原厂名称");
        validateRequired(authStartDateStr, "授权开始时间");
        validateRequired(authEndDateStr, "授权结束时间");

        if (!authEndDate.isAfter(authStartDate)) {
            throw new BusinessException("结束时间须晚于开始时间");
        }

        ManufacturerAuthorization auth = ManufacturerAuthorization.create(
                productLine, brandId, brandName, importDomestic, manufacturerName,
                authStartDate, authEndDate, remarks, userId);

        ManufacturerAuthorization saved = repository.save(auth);
        recordOpLog(saved, userId, "批量导入原厂授权");
    }

    private void importAgentRow(final Row row, final Long userId) {
        int col = 0;
        String productLineStr = getCellString(row, col++);
        String brandId = getCellString(row, col++);
        String brandName = getCellString(row, col++);
        String importDomestic = getCellString(row, col++);
        String manufacturerName = getCellString(row, col++);
        // col 5: 授权1附件文件名 (skip)
        col++;
        String auth1StartDateStr = getCellString(row, col++);
        String auth1EndDateStr = getCellString(row, col++);
        String auth1Remarks = getCellString(row, col++);
        String agentName = getCellString(row, col++);
        // col 10: 授权2附件文件名 (skip)
        col++;
        String auth2StartDateStr = getCellString(row, col++);
        String auth2EndDateStr = getCellString(row, col++);
        String auth2Remarks = getCellString(row, col++);

        ProductLine productLine = parseProductLine(productLineStr);
        LocalDate auth1StartDate = parseDate(auth1StartDateStr);
        LocalDate auth1EndDate = parseDate(auth1EndDateStr);
        LocalDate auth2StartDate = parseDate(auth2StartDateStr);
        LocalDate auth2EndDate = parseDate(auth2EndDateStr);

        validateRequired(productLineStr, "一级产线");
        validateRequired(brandId, "品牌ID");
        validateRequired(brandName, "品牌");
        validateRequired(importDomestic, "进口/国产");
        validateRequired(manufacturerName, "品牌原厂名称");
        validateRequired(agentName, "代理商名称");
        validateRequired(auth1StartDateStr, "授权1开始时间");
        validateRequired(auth1EndDateStr, "授权1结束时间");
        validateRequired(auth2StartDateStr, "授权2开始时间");
        validateRequired(auth2EndDateStr, "授权2结束时间");

        if (!auth1EndDate.isAfter(auth1StartDate)) {
            throw new BusinessException("授权1结束时间须晚于开始时间");
        }
        if (!auth2EndDate.isAfter(auth2StartDate)) {
            throw new BusinessException("授权2结束时间须晚于开始时间");
        }
        if (auth2StartDate.isBefore(auth1StartDate)) {
            throw new BusinessException("授权2开始时间不能早于授权1开始时间");
        }
        if (auth2EndDate.isAfter(auth1EndDate)) {
            throw new BusinessException("授权2结束时间不能晚于授权1结束时间");
        }

        ManufacturerAuthorization auth = ManufacturerAuthorization.createAgent(
                productLine, brandId, brandName, importDomestic, manufacturerName, agentName,
                auth2StartDate, auth2EndDate,
                auth1StartDate, auth1EndDate, auth1Remarks,
                auth2StartDate, auth2EndDate, auth2Remarks,
                null, userId);

        ManufacturerAuthorization saved = repository.save(auth);
        recordOpLog(saved, userId, "批量导入代理商授权");
    }

    private void recordOpLog(final ManufacturerAuthorization auth, final Long userId, final String summary) {
        String operatorUsername = "system";
        if (userId != null) {
            operatorUsername = userRepository.findById(userId)
                    .map(u -> u.getFullName() + "(" + u.getUsername() + ")")
                    .orElse("system");
        }
        String detailsJson = String.format(
                "{\"authorizationType\":\"%s\",\"productLine\":\"%s\",\"brandId\":\"%s\",\"brandName\":\"%s\",\"importDomestic\":\"%s\",\"manufacturerName\":\"%s\"}",
                auth.authorizationType(), auth.productLine().getDisplayName(),
                auth.brandId(), auth.brandName(), auth.importDomestic(), auth.manufacturerName());

        BrandAuthOperationLogEntity opLog = BrandAuthOperationLogEntity.builder()
                .authorizationId(auth.id())
                .operatorId(userId)
                .operatorUsername(operatorUsername)
                .actionType("IMPORT")
                .summary(summary)
                .details(detailsJson)
                .remarks("批量导入")
                .build();
        logRepository.save(opLog);
    }

    // --- Utility methods ---

    private static String getCellString(final Row row, final int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double val = cell.getNumericCellValue();
                yield val == Math.floor(val) && !Double.isInfinite(val)
                        ? String.valueOf((long) val) : String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try { yield String.valueOf((int) cell.getNumericCellValue()); }
                catch (RuntimeException e) {
                    try { yield cell.getStringCellValue().trim(); }
                    catch (RuntimeException e2) { yield ""; }
                }
            }
            default -> "";
        };
    }

    private static LocalDate parseDate(final String str) {
        if (str == null || str.isBlank()) return null;
        try {
            return LocalDate.parse(str.trim(), DATE_FMT);
        } catch (DateTimeParseException e) {
            throw new BusinessException("日期格式错误 (" + str + ")，应为 yyyy-MM-dd");
        }
    }

    private static ProductLine parseProductLine(final String str) {
        if (str == null || str.isBlank()) return null;
        return ProductLine.fromStringOptional(str)
                .orElseThrow(() -> new BusinessException("无效的一级产线: " + str));
    }

    private static void validateRequired(final String value, final String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(fieldName + "不能为空");
        }
    }

    /** Result with per-sheet details. */
    public static final class ImportResult {
        private final List<SheetResult> sheets = new ArrayList<>();
        private int totalRows;
        private int totalSuccess;
        private int totalFailed;

        void addSheetResult(final String sheetName, final int totalRows,
                            final int successCount, final List<String> errors) {
            this.sheets.add(new SheetResult(sheetName, totalRows, successCount, errors));
            this.totalRows += totalRows;
            this.totalSuccess += successCount;
            this.totalFailed += errors.size();
        }

        public List<SheetResult> getSheets() { return sheets; }
        public int getTotalRows() { return totalRows; }
        public int getTotalSuccess() { return totalSuccess; }
        public int getTotalFailed() { return totalFailed; }
    }

    /** Per-sheet result. */
    public static final class SheetResult {
        private final String sheetName;
        private final int totalRows;
        private final int successCount;
        private final List<String> errors;

        SheetResult(final String sheetName, final int totalRows,
                    final int successCount, final List<String> errors) {
            this.sheetName = sheetName;
            this.totalRows = totalRows;
            this.successCount = successCount;
            this.errors = errors;
        }

        public String getSheetName() { return sheetName; }
        public int getTotalRows() { return totalRows; }
        public int getSuccessCount() { return successCount; }
        public int getFailedCount() { return errors.size(); }
        public List<String> getErrors() { return errors; }
    }
}
