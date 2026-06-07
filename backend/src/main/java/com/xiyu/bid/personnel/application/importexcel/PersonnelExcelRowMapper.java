package com.xiyu.bid.personnel.application.importexcel;

import com.xiyu.bid.personnel.domain.importvalidation.ParsedCertificateRow;
import com.xiyu.bid.personnel.domain.importvalidation.ParsedEducationRow;
import com.xiyu.bid.personnel.domain.importvalidation.ParsedPersonnelRow;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Excel 行 → 纯核心 Parsed*Row 的映射器（应用层）
 * 负责从 POI Row 中安全读取数据，并转换为领域可用的不可变对象。
 */
public final class PersonnelExcelRowMapper {

    private PersonnelExcelRowMapper() {}

    public static List<ParsedPersonnelRow> toPersonnelRows(List<Row> rows) {
        List<ParsedPersonnelRow> result = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            // 假设第0行是表头，从第1行开始是数据（Excel行号从1开始）
            int excelRowNumber = i + 2;
            ParsedPersonnelRow parsed = new ParsedPersonnelRow(
                    excelRowNumber,
                    getStringCell(row, 0),   // 工号
                    getStringCell(row, 1),   // 姓名
                    getStringCell(row, 2),   // 性别
                    getDateCell(row, 3),     // 入职时间
                    getStringCell(row, 4),   // 手机
                    getStringCell(row, 5),   // 学历
                    getStringCell(row, 6)    // 技术职称
            );
            result.add(parsed);
        }
        return result;
    }

    public static List<ParsedEducationRow> toEducationRows(List<Row> rows) {
        List<ParsedEducationRow> result = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int excelRowNumber = i + 2;
            result.add(new ParsedEducationRow(
                    excelRowNumber,
                    getStringCell(row, 0),   // 工号
                    getStringCell(row, 1),   // 姓名（用于交叉校验）
                    getStringCell(row, 2),   // 学校
                    getDateCell(row, 3),     // 入学
                    getDateCell(row, 4),     // 毕业
                    getStringCell(row, 5),   // 最高学历
                    getStringCell(row, 6),   // 学习形式
                    getStringCell(row, 7)    // 专业
            ));
        }
        return result;
    }

    public static List<ParsedCertificateRow> toCertificateRows(List<Row> rows) {
        List<ParsedCertificateRow> result = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int excelRowNumber = i + 2;
            result.add(new ParsedCertificateRow(
                    excelRowNumber,
                    getStringCell(row, 0),   // 工号
                    getStringCell(row, 1),   // 姓名
                    getStringCell(row, 2),   // 证书名称
                    getStringCell(row, 3),   // 证书编号
                    getStringCell(row, 4),   // 类型
                    getDateCell(row, 5),     // 发证日期
                    getDateCell(row, 6),     // 有效期
                    getStringCell(row, 7)    // 附件文件名（原始）
            ));
        }
        return result;
    }

    // ========== 安全读取工具 ==========

    private static String getStringCell(Row row, int colIndex) {
        if (row == null) return null;
        Cell cell = row.getCell(colIndex);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            default -> null;
        };
    }

    private static LocalDate getDateCell(Row row, int colIndex) {
        if (row == null) return null;
        Cell cell = row.getCell(colIndex);
        if (cell == null) return null;
        if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
            return cell.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        // 也支持字符串格式，后续可加强
        return null;
    }
}
