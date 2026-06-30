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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
/** Batch import brand authorizations from Excel. */
@Service
@RequiredArgsConstructor
public class BrandAuthImportService {
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
        String productLineStr = BrandAuthImportParser.getCellString(row, col++);
        String brandId = BrandAuthImportParser.getCellString(row, col++);
        String brandName = BrandAuthImportParser.getCellString(row, col++);
        String importDomestic = BrandAuthImportParser.getCellString(row, col++);
        String manufacturerName = BrandAuthImportParser.getCellString(row, col++);
        // col 5: 原厂授权附件文件名 (skip for import - handled separately)
        col++;
        String authStartDateStr = BrandAuthImportParser.getCellString(row, col++);
        String authEndDateStr = BrandAuthImportParser.getCellString(row, col++);
        String remarks = BrandAuthImportParser.getCellString(row, col++);
        // col 9: 补充材料附件文件名 (skip)
        // col++

        ProductLine productLine = BrandAuthImportParser.parseProductLine(productLineStr);
        LocalDate authStartDate = BrandAuthImportParser.parseDate(authStartDateStr);
        LocalDate authEndDate = BrandAuthImportParser.parseDate(authEndDateStr);

        BrandAuthImportParser.validateRequired(productLineStr, "一级产线");
        BrandAuthImportParser.validateRequired(brandId, "品牌ID");
        BrandAuthImportParser.validateRequired(brandName, "品牌");
        BrandAuthImportParser.validateRequired(importDomestic, "进口/国产");
        BrandAuthImportParser.validateRequired(manufacturerName, "品牌原厂名称");
        BrandAuthImportParser.validateRequired(authStartDateStr, "授权开始时间");
        BrandAuthImportParser.validateRequired(authEndDateStr, "授权结束时间");

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
        String productLineStr = BrandAuthImportParser.getCellString(row, col++);
        String brandId = BrandAuthImportParser.getCellString(row, col++);
        String brandName = BrandAuthImportParser.getCellString(row, col++);
        String importDomestic = BrandAuthImportParser.getCellString(row, col++);
        String manufacturerName = BrandAuthImportParser.getCellString(row, col++);
        // col 5: 授权1附件文件名 (skip)
        col++;
        String auth1StartDateStr = BrandAuthImportParser.getCellString(row, col++);
        String auth1EndDateStr = BrandAuthImportParser.getCellString(row, col++);
        String auth1Remarks = BrandAuthImportParser.getCellString(row, col++);
        String agentName = BrandAuthImportParser.getCellString(row, col++);
        // col 10: 授权2附件文件名 (skip)
        col++;
        String auth2StartDateStr = BrandAuthImportParser.getCellString(row, col++);
        String auth2EndDateStr = BrandAuthImportParser.getCellString(row, col++);
        String auth2Remarks = BrandAuthImportParser.getCellString(row, col++);

        ProductLine productLine = BrandAuthImportParser.parseProductLine(productLineStr);
        LocalDate auth1StartDate = BrandAuthImportParser.parseDate(auth1StartDateStr);
        LocalDate auth1EndDate = BrandAuthImportParser.parseDate(auth1EndDateStr);
        LocalDate auth2StartDate = BrandAuthImportParser.parseDate(auth2StartDateStr);
        LocalDate auth2EndDate = BrandAuthImportParser.parseDate(auth2EndDateStr);

        BrandAuthImportParser.validateRequired(productLineStr, "一级产线");
        BrandAuthImportParser.validateRequired(brandId, "品牌ID");
        BrandAuthImportParser.validateRequired(brandName, "品牌");
        BrandAuthImportParser.validateRequired(importDomestic, "进口/国产");
        BrandAuthImportParser.validateRequired(manufacturerName, "品牌原厂名称");
        BrandAuthImportParser.validateRequired(agentName, "代理商名称");
        BrandAuthImportParser.validateRequired(auth1StartDateStr, "授权1开始时间");
        BrandAuthImportParser.validateRequired(auth1EndDateStr, "授权1结束时间");
        BrandAuthImportParser.validateRequired(auth2StartDateStr, "授权2开始时间");
        BrandAuthImportParser.validateRequired(auth2EndDateStr, "授权2结束时间");

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
        // 操作日志详情：中文可读格式（与 Create/Update 操作日志风格统一）
        StringBuilder details = new StringBuilder();
        boolean isAgent = "AGENT".equals(auth.authorizationType());
        details.append("授权类型：").append(isAgent ? "代理商授权" : "原厂授权");
        details.append("; 产线：").append(auth.productLine().getDisplayName());
        details.append("; 品牌ID：").append(auth.brandId());
        details.append("; 品牌名：").append(auth.brandName());
        details.append("; 进口/国产：").append(auth.importDomestic());
        details.append("; 原厂：").append(auth.manufacturerName());
        if (isAgent && auth.agentName() != null && !auth.agentName().isBlank()) {
            details.append("; 代理商：").append(auth.agentName());
        }

        BrandAuthOperationLogEntity opLog = BrandAuthOperationLogEntity.builder()
                .authorizationId(auth.id())
                .operatorId(userId)
                .operatorUsername(operatorUsername)
                .actionType("IMPORT")
                .summary(summary)
                .details(details.toString())
                .remarks("批量导入")
                .build();
        logRepository.save(opLog);
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
