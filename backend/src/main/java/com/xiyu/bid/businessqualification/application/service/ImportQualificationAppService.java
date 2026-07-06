// Input: multipart Excel upload (11 列), current user operator name
// Output: per-row success/failure with command payload for downstream create
// Pos: Application service 编排 + 校验，副作用下沉到 service 层 Excel 解析
// 维护声明: 仅做 Excel → RowInput 解析 + 行级校验；入库走 CreateQualificationAppService
package com.xiyu.bid.businessqualification.application.service;

import com.xiyu.bid.businessqualification.application.command.QualificationImportRowResult;
import com.xiyu.bid.businessqualification.application.command.QualificationUpsertCommand;
import com.xiyu.bid.businessqualification.infrastructure.persistence.repository.BusinessQualificationJpaRepository;
import com.xiyu.bid.infrastructure.excel.ExcelCellFormatter;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.xiyu.bid.common.domain.CommonDateParser;
import com.xiyu.bid.exception.InvalidArgumentException;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * §4.1.3.4 资质批量导入应用层
 *
 * 职责：
 *  1. 解析上传的 11 列 Excel
 *  2. 逐行校验（必填/长度/格式/日期/查重）
 *  3. 对校验通过的行调用 CreateQualificationAppService 入库
 *  4. 返回行级结果汇总 {success, failed, results[]}
 *
 * 失败行不中断整体导入；证书编号已存在则整行跳过。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportQualificationAppService {

    private final BusinessQualificationJpaRepository jpaRepository;
    private final CreateQualificationAppService createAppService;

    public record ImportSummary(
            int total,
            int success,
            int failed,
            List<QualificationImportRowResult> results
    ) {
        public static ImportSummary empty() {
            return new ImportSummary(0, 0, 0, new ArrayList<>());
        }
    }

    /**
     * 解析 + 校验 + 入库（事务内）。失败行不抛异常。
     */
    @Transactional
    public ImportSummary importFromExcel(MultipartFile file, String operatorName) throws IOException {
List<RowInput> rows;
        try (Workbook wb = new XSSFWorkbook(file.getInputStream())) {
            rows = parse(wb);
        }
if (rows.isEmpty()) {
            return ImportSummary.empty();
        }
        List<QualificationImportRowResult> results = new ArrayList<>(rows.size());
        int success = 0;
        int failed = 0;
        for (RowInput row : rows) {
            QualificationImportRowResult result = processRow(row, operatorName);
            results.add(result);
            if (result.isSuccess()) {
                success++;
            } else {
                failed++;
            }
        }
        return new ImportSummary(rows.size(), success, failed, results);
    }

    private QualificationImportRowResult processRow(RowInput row, String operatorName) {
        // 必填校验
        if (isBlank(row.name())) return fail(row.rowNumber(), row.certificateNo(), "证书名称不能为空");
        if (isBlank(row.issuer())) return fail(row.rowNumber(), row.certificateNo(), "认证机构不能为空");
        if (isBlank(row.certificateNo())) return fail(row.rowNumber(), row.certificateNo(), "证书编号不能为空");
        if (isBlank(row.issueDate())) return fail(row.rowNumber(), row.certificateNo(), "发证日期不能为空");
        if (isBlank(row.expiryDate())) return fail(row.rowNumber(), row.certificateNo(), "证书有效期不能为空");
        if (isBlank(row.agency())) return fail(row.rowNumber(), row.certificateNo(), "代理机构不能为空");
        if (isBlank(row.agencyContact())) return fail(row.rowNumber(), row.certificateNo(), "代理机构联系人不能为空");
        if (isBlank(row.certScope())) return fail(row.rowNumber(), row.certificateNo(), "认证范围不能为空");
        if (isBlank(row.attachmentFileName())) return fail(row.rowNumber(), row.certificateNo(), "附件文件名不能为空");

        // 长度校验
        if (row.name().length() > 200) return fail(row.rowNumber(), row.certificateNo(), "证书名称超过200字符");
        if (row.level() != null && row.level().length() > 50) {
            return fail(row.rowNumber(), row.certificateNo(), "等级超过50字符");
        }
        if (row.issuer().length() > 200) return fail(row.rowNumber(), row.certificateNo(), "认证机构超过200字符");
        if (row.certificateNo().length() > 120) return fail(row.rowNumber(), row.certificateNo(), "证书编号超过120字符");
        if (row.agency().length() > 200) return fail(row.rowNumber(), row.certificateNo(), "代理机构超过200字符");
        if (row.agencyContact().length() > 200) return fail(row.rowNumber(), row.certificateNo(), "代理机构联系人超过200字符");
        if (row.certScope().length() > 1000) return fail(row.rowNumber(), row.certificateNo(), "认证范围超过1000字符");
        if (row.certReviewNote() != null && row.certReviewNote().length() > 200) {
            return fail(row.rowNumber(), row.certificateNo(), "证书审核提醒超过200字符");
        }

        // 日期解析 + 顺序
        java.time.LocalDate issueDate, expiryDate;
        try {
            issueDate = CommonDateParser.parseDayPrecisionOrThrow(row.issueDate().trim(), "发证日期");
        } catch (IllegalArgumentException e) {
            return fail(row.rowNumber(), row.certificateNo(), e.getMessage());
        }
        try {
            expiryDate = CommonDateParser.parseDayPrecisionOrThrow(row.expiryDate().trim(), "证书有效期");
        } catch (IllegalArgumentException e) {
            return fail(row.rowNumber(), row.certificateNo(), e.getMessage());
        }
        if (!expiryDate.isAfter(issueDate)) {
            return fail(row.rowNumber(), row.certificateNo(), "证书有效期须晚于发证日期");
        }

        // 证书编号查重
        if (jpaRepository.existsByCertificateNo(row.certificateNo().trim())) {
            return fail(row.rowNumber(), row.certificateNo(), "证书编号已存在");
        }

        // 附件命名格式：QUAL_{证书编号}_{序号}_{文件名}.{扩展名}
        if (!row.attachmentFileName().startsWith("QUAL_" + row.certificateNo().trim() + "_")) {
            return fail(row.rowNumber(), row.certificateNo(), "附件文件名命名格式不符（应 QUAL_" + row.certificateNo().trim() + "_NN_xxx.ext）");
        }

        // 入库
        String operator = (operatorName == null || operatorName.isBlank()) ? "系统导入" : operatorName;
        QualificationUpsertCommand command = QualificationUpsertCommand.builder()
                .name(row.name().trim())
                .level(isBlank(row.level()) ? null : row.level().trim())
                .subjectType(com.xiyu.bid.businessqualification.domain.valueobject.QualificationSubjectType.COMPANY)
                .subjectName(operator)
                .category(com.xiyu.bid.businessqualification.domain.valueobject.QualificationCategory.OTHER)
                .certificateNo(row.certificateNo().trim())
                .issuer(row.issuer().trim())
                .agency(row.agency().trim())
                .agencyContact(row.agencyContact().trim())
                .certScope(row.certScope().trim())
                .certReviewNote(isBlank(row.certReviewNote()) ? null : row.certReviewNote().trim())
                .holderName(operator)
                .issueDate(issueDate)
                .expiryDate(expiryDate)
                .reminderEnabled(true)
                .reminderDays(30)
                .fileUrl(row.attachmentFileName().trim())
                .attachments(List.of())
                .build();

        try {
            createAppService.create(command);
            return QualificationImportRowResult.success(row.rowNumber(), row.certificateNo().trim(), command);
        } catch (InvalidArgumentException e) {
            return fail(row.rowNumber(), row.certificateNo(), e.getMessage());
        } catch (RuntimeException e) {
            log.error("资质导入第{}行入库失败: {}", row.rowNumber(), row.certificateNo(), e);
            return fail(row.rowNumber(), row.certificateNo(), "第" + row.rowNumber() + "行系统错误，请联系管理员");
        }
    }

    /* ---------------- Excel 解析 ---------------- */

    public record RowInput(
            int rowNumber,
            String name,
            String level,
            String issuer,
            String certificateNo,
            String issueDate,
            String expiryDate,
            String agency,
            String agencyContact,
            String certScope,
            String certReviewNote,
            String attachmentFileName
    ) {}

    private static List<RowInput> parse(Workbook wb) {
        List<RowInput> rows = new ArrayList<>();
        Sheet sheet = wb.getSheetAt(0);
        if (sheet == null) return rows;
        DataFormatter formatter = new DataFormatter();
        FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
        // 跳过表头（行 0），从行 1 开始；行号 = 物理行号 + 1（人类阅读用 1-based）
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            String name = readCell(row.getCell(0), formatter, evaluator);
            String level = readCell(row.getCell(1), formatter, evaluator);
            String issuer = readCell(row.getCell(2), formatter, evaluator);
            String certNo = readCertificateNo(row.getCell(3));
            String issueDate = readCell(row.getCell(4), formatter, evaluator);
            String expiryDate = readCell(row.getCell(5), formatter, evaluator);
            String agency = readCell(row.getCell(6), formatter, evaluator);
            String agencyContact = readCell(row.getCell(7), formatter, evaluator);
            String certScope = readCell(row.getCell(8), formatter, evaluator);
            String certReviewNote = readCell(row.getCell(9), formatter, evaluator);
            String attachmentFileName = readCell(row.getCell(10), formatter, evaluator);
            // 完全空行跳过
            if (isBlank(name) && isBlank(certNo) && isBlank(issuer)) continue;
            rows.add(new RowInput(r + 1, name, level, issuer, certNo, issueDate, expiryDate, agency, agencyContact, certScope, certReviewNote, attachmentFileName));
        }
        return rows;
    }

    private static String readCell(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) return null;
        String value = ExcelCellFormatter.formatCell(cell, formatter, evaluator);
        return value.isEmpty() ? null : value;
    }

    /**
     * 读取证书编号原始值，避免 Excel 数字格式（如 0.00、#,##0）影响业务标识符。
     * 数字类型按原始 double 值处理：整数值转为 long 字符串，小数值保留原样。
     */
    private static String readCertificateNo(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case NUMERIC -> formatNumericCertificateNo(cell.getNumericCellValue());
            case FORMULA -> formatNumericCertificateNo(cell.getNumericCellValue());
            case STRING -> cell.getStringCellValue();
            default -> null;
        };
    }

    private static String formatNumericCertificateNo(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    /* ---------------- helpers ---------------- */

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static QualificationImportRowResult fail(int rowNumber, String certificateNo, String reason) {
        return QualificationImportRowResult.failure(rowNumber, certificateNo == null ? "" : certificateNo, reason);
    }
}
