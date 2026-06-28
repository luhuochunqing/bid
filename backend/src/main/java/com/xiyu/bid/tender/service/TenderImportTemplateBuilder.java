// Input: 字典枚举（地区/客户类型/优先级）+ 标题列定义
// Output: 标讯批量导入 .xlsx 模板字节流
// Pos: service/导入模板生成器
// 维护声明: 仅负责模板布局/字典/列宽；表头常量与 TenderImportService.HEADERS 共享，修改需同步。

package com.xiyu.bid.tender.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import com.xiyu.bid.tender.core.TenderRegionCatalog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * 标讯批量导入模板生成器：
 * <ul>
 *   <li>Sheet1「标讯导入」：表头 + 1 行示例数据；必填列红色背景，可选列灰色背景。</li>
 *   <li>Sheet2「字典参考」：列出地区 / 客户类型 / 优先级合法值。</li>
 * </ul>
 */
@Component
public class TenderImportTemplateBuilder {

    private static final String[] EXAMPLE_ROW = {
            "示例：XX数据中心机房改造项目",
            "XX集团有限公司",
            "北京市-北京市",
            "2026-12-31 17:00",
            "2026-12-25 09:30",
            "张三",
            "13800138000",
            "010-12345678",
            "zhangsan@example.com",
            "李四",
            "13900139000",
            "021-87654321",
            "lisi@example.com",
            "央企",
            "A",
            "工业品",
            "政府采购网",
            "含场地改造、综合布线、机柜安装"
    };

    public byte[] build() {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            buildMainSheet(workbook);
            buildDictionarySheet(workbook);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("生成标讯导入模板失败", e);
        }
    }

    private void buildMainSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("标讯导入");
        CellStyle requiredStyle = headerStyle(workbook, IndexedColors.ROSE.getIndex());
        CellStyle optionalStyle = headerStyle(workbook, IndexedColors.GREY_25_PERCENT.getIndex());

        Row header = sheet.createRow(0);
        String[] headers = TenderImportService.HEADERS;
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headers[i].endsWith("*") ? requiredStyle : optionalStyle);
            sheet.setColumnWidth(i, columnWidth(headers[i]));
        }

        Row example = sheet.createRow(1);
        for (int i = 0; i < EXAMPLE_ROW.length && i < headers.length; i++) {
            example.createCell(i).setCellValue(EXAMPLE_ROW[i]);
        }
    }

    private void buildDictionarySheet(Workbook workbook) {
        Sheet dict = workbook.createSheet("字典参考");
        CellStyle headerStyle = headerStyle(workbook, IndexedColors.GREY_25_PERCENT.getIndex());

        String[] columns = {"地区（总部所在地：一级+二级，如广东省深圳市、北京市北京市）", "客户类型", "优先级", "项目类型"};
        Row header = dict.createRow(0);
        for (int i = 0; i < columns.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(columns[i]);
            cell.setCellStyle(headerStyle);
            dict.setColumnWidth(i, 5500);
        }

        List<String> regions = TenderImportService.REGIONS.stream()
                // 字典 sheet 只展示推荐的一级+二级格式，过滤掉旧兼容格式（单名、市-市）
                // 避免 Excel 导入用户困惑该填哪种；TenderRegionCatalog.isValid 仍接受所有格式
                .filter(r -> !TenderRegionCatalog.PROVINCE_ONLY.contains(r))
                .filter(r -> !r.matches(".*-.*"))
                .toList();
        List<String> customerTypes = TenderImportService.CUSTOMER_TYPES;
        List<String> priorities = TenderImportService.PRIORITIES;
        List<String> projectTypes = TenderImportService.PROJECT_TYPES;
        int total = Math.max(regions.size(),
                Math.max(customerTypes.size(), Math.max(priorities.size(), projectTypes.size())));
        for (int r = 0; r < total; r++) {
            Row row = dict.createRow(r + 1);
            if (r < regions.size()) row.createCell(0).setCellValue(regions.get(r));
            if (r < customerTypes.size()) row.createCell(1).setCellValue(customerTypes.get(r));
            if (r < priorities.size()) row.createCell(2).setCellValue(priorities.get(r));
            if (r < projectTypes.size()) row.createCell(3).setCellValue(projectTypes.get(r));
        }
    }

    private CellStyle headerStyle(Workbook workbook, short backgroundColor) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(backgroundColor);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }
    private int columnWidth(String header) {
        if (header.contains("时间")) return 6000;
        if (header.contains("描述")) return 9000;
        if (header.contains("主体")) return 7000;
        if (header.contains("项目名称")) return 9000;
        return 5000;
    }
}
