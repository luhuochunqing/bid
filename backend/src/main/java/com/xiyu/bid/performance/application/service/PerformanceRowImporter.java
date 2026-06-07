package com.xiyu.bid.performance.application.service;

import com.xiyu.bid.performance.application.command.PerformanceUpsertCommand;
import com.xiyu.bid.performance.domain.port.PerformanceRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

import static com.xiyu.bid.performance.application.service.PerformanceEnumLabels.parseCustomerLevel;
import static com.xiyu.bid.performance.application.service.PerformanceEnumLabels.parseCustomerType;
import static com.xiyu.bid.performance.application.service.PerformanceEnumLabels.parseDockingMethod;
import static com.xiyu.bid.performance.application.service.PerformanceEnumLabels.parseProjectType;

/**
 * 业绩单行导入服务（独立事务，避免自调用代理陷阱）
 */
@Service
@RequiredArgsConstructor
public class PerformanceRowImporter {

    private final PerformanceRepository repository;
    private final CreatePerformanceAppService createService;
    private final UpdatePerformanceAppService updateService;

    private static final DateTimeFormatter DTF = DateTimeFormatter.ISO_LOCAL_DATE;

    @Transactional
    public void importRow(Row row, int rowNum) {
        String contractName = getCellStr(row, 0);
        if (contractName == null || contractName.isBlank()) {
            throw new IllegalArgumentException("合同名称不能为空");
        }
        LocalDate signingDate = parseDate(getCellStr(row, 9));
        LocalDate expiryDate = parseDate(getCellStr(row, 10));
        if (signingDate != null && expiryDate != null && !expiryDate.isAfter(signingDate)) {
            throw new IllegalArgumentException("截止日期须晚于签约日期");
        }
        var cmd = new PerformanceUpsertCommand(
                contractName, getCellStr(row, 1), getCellStr(row, 2),
                parseCustomerType(getCellStr(row, 3)), getCellStr(row, 4),
                parseProjectType(getCellStr(row, 5)), parseDockingMethod(getCellStr(row, 6)),
                parseCustomerLevel(getCellStr(row, 7)),
                signingDate, expiryDate, parseDate(getCellStr(row, 11)),
                getCellStr(row, 14), getCellStr(row, 15), getCellStr(row, 16),
                getCellStr(row, 17), getCellStr(row, 18), getCellStr(row, 20),
                "是".equals(getCellStr(row, 25)), getCellStr(row, 27),
                List.of());
        var existing = repository.findByContractName(contractName);
        if (existing.isPresent()) {
            updateService.update(existing.get().id(), cmd);
        } else {
            createService.create(cmd);
        }
    }

    private String getCellStr(Row row, int idx) {
        var cell = row.getCell(idx);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getDateCellValue().toInstant()
                            .atZone(ZoneId.systemDefault()).toLocalDate().toString();
                } else {
                    double val = cell.getNumericCellValue();
                    yield val == Math.rint(val) ? String.valueOf((long) val) : String.valueOf(val);
                }
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
    }

    private LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDate.parse(s, DTF); }
        catch (DateTimeParseException e) { throw new IllegalArgumentException("日期格式错误: " + s + "，应为 YYYY-MM-DD"); }
    }
}
