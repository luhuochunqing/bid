package com.xiyu.bid.warehouse.infrastructure;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 基础设施：仓库导入 Excel 读取器 — 将上传的 .xlsx 解析为字符串二维表。
 * 不含业务校验，业务校验在 WarehouseImportPolicy 纯核心内。
 */
@Component
public class WarehouseImportExcelReader {

    public SheetData read(byte[] fileBytes) throws IOException {
        try (InputStream in = new ByteArrayInputStream(fileBytes);
             Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            int lastRow = sheet.getLastRowNum();
            List<String[]> rows = new ArrayList<>(lastRow + 1);
            for (int i = 0; i <= lastRow; i++) {
                Row r = sheet.getRow(i);
                if (r == null) {
                    rows.add(new String[0]);
                    continue;
                }
                int lastCell = r.getLastCellNum();
                String[] cells = new String[lastCell];
                for (int c = 0; c < lastCell; c++) {
                    Cell cell = r.getCell(c);
                    cells[c] = cell == null ? "" : fmt.formatCellValue(cell).trim();
                }
                rows.add(cells);
            }
            return new SheetData(rows);
        }
    }

    public static class SheetData {
        public final List<String[]> rows;

        public SheetData(List<String[]> rows) {
            this.rows = rows;
        }

        public String[] header() {
            return rows.isEmpty() ? new String[0] : rows.get(0);
        }

        public List<String[]> dataRows() {
            return rows.size() <= 1 ? List.of() : rows.subList(1, rows.size());
        }
    }
}
