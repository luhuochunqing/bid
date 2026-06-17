package com.xiyu.bid.infrastructure.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 通用单 Sheet Excel 读取器，用于批量导入。
 * 各导入模块通过依赖注入复用，无需分别创建。
 */
@Component
public class SingleSheetExcelReader {

    public WorkbookData read(byte[] fileBytes) throws IOException {
        try (var in = new ByteArrayInputStream(fileBytes);
             var wb = new XSSFWorkbook(in)) {
            DataFormatter fmt = new DataFormatter();
            return new WorkbookData(readSheet(wb.getSheetAt(0), fmt));
        }
    }

    private List<String[]> readSheet(Sheet sheet, DataFormatter fmt) {
        int lastRow = sheet.getLastRowNum();
        List<String[]> rows = new ArrayList<>(lastRow + 1);
        for (int i = 0; i <= lastRow; i++) {
            Row r = sheet.getRow(i);
            if (r == null) { rows.add(new String[0]); continue; }
            int lastCell = r.getLastCellNum();
            String[] cells = new String[lastCell];
            for (int c = 0; c < lastCell; c++) {
                Cell cell = r.getCell(c);
                cells[c] = cell == null ? "" : fmt.formatCellValue(cell).trim();
            }
            rows.add(cells);
        }
        return rows;
    }

    public record WorkbookData(List<String[]> sheetRows) {
        public String[] header() { return sheetRows.isEmpty() ? new String[0] : sheetRows.get(0); }
        public List<String[]> data() { return sheetRows.size() <= 1 ? List.of() : sheetRows.subList(1, sheetRows.size()); }
    }
}
