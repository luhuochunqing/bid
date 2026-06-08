package com.xiyu.bid.personnel.application.importexcel;

import com.xiyu.bid.personnel.domain.importvalidation.ParsedCertificateRow;
import com.xiyu.bid.personnel.domain.importvalidation.ParsedEducationRow;
import com.xiyu.bid.personnel.domain.importvalidation.ParsedPersonnelRow;
import com.xiyu.bid.personnel.domain.importvalidation.PersonnelImportValidator;
import com.xiyu.bid.personnel.domain.importvalidation.ValidationResult;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 人员证书批量导入 Excel 解析器（应用层）
 *
 * 职责：
 * - 读取 3 个 Sheet 的 Excel 文件
 * - 使用 PersonnelExcelRowMapper 转换为纯核心 Parsed*Row
 * - 调用 PersonnelImportValidator 进行校验
 * - 返回结构化结果（ImportParseResult）
 *
 * 不负责：
 * - 附件文件处理（后续单独处理）
 * - 持久化与异步执行（由上层 AppService 负责）
 */
@Component
public class PersonnelExcelImportParser {

    /**
     * 解析上传的 Excel 文件（必须包含 3 个指定 Sheet）
     */
    public ImportParseResult parse(InputStream inputStream) {
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {

            // 假设 Sheet 顺序固定：0=基础信息, 1=教育经历, 2=证书与职称
            Sheet sheet1 = workbook.getSheetAt(0);
            Sheet sheet2 = workbook.getSheetAt(1);
            Sheet sheet3 = workbook.getSheetAt(2);

            List<ParsedPersonnelRow> personnelRows = PersonnelExcelRowMapper.toPersonnelRows(
                    getDataRows(sheet1)
            );

            List<ParsedEducationRow> educationRows = PersonnelExcelRowMapper.toEducationRows(
                    getDataRows(sheet2)
            );

            List<ParsedCertificateRow> certificateRows = PersonnelExcelRowMapper.toCertificateRows(
                    getDataRows(sheet3)
            );

            // 调用纯核心校验器
            ValidationResult validationResult = PersonnelImportValidator.validate(
                    personnelRows,
                    educationRows,
                    certificateRows
            );

            return new ImportParseResult(
                    personnelRows,
                    educationRows,
                    certificateRows,
                    validationResult
            );

        } catch (IOException e) {
            throw new IllegalArgumentException("Excel 文件解析失败：" + e.getMessage(), e);
        } catch (IllegalStateException e) {
            throw new IllegalArgumentException("Excel 文件解析失败：" + e.getMessage(), e);
        }
    }

    /**
     * 提取 Sheet 中除表头外的所有数据行
     */
    private List<org.apache.poi.ss.usermodel.Row> getDataRows(Sheet sheet) {
        List<org.apache.poi.ss.usermodel.Row> dataRows = new ArrayList<>();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {  // 跳过第0行表头
            org.apache.poi.ss.usermodel.Row row = sheet.getRow(i);
            if (row != null) {
                dataRows.add(row);
            }
        }
        return dataRows;
    }
}
