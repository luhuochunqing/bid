package com.xiyu.bid.platform.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.platform.dto.PlatformAccountCreateRequest;
import com.xiyu.bid.platform.entity.PlatformAccount.PlatformType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Platform account Excel batch import service. */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformAccountImportService {

    private static final String[] TEMPLATE_HEADERS = {
            "平台名称*", "平台类型*", "登录账号*", "登录密码*",
            "平台URL", "联系人", "联系电话", "联系邮箱", "备注"
    };
    private static final Map<String, PlatformType> TYPE_MAP = Map.of(
            "政府采购网", PlatformType.GOV_PROCUREMENT,
            "招投标平台", PlatformType.BIDDING_PLATFORM,
            "建设工程平台", PlatformType.CONSTRUCTION_PLATFORM,
            "其他", PlatformType.OTHER
    );

    private final PlatformAccountService platformAccountService;

    /** Generate template .xlsx bytes. */
    public byte[] generateTemplate() {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("平台账号导入模板");
            CellStyle headerStyle = wb.createCellStyle();
            Font font = wb.createFont();
            font.setBold(true);
            headerStyle.setFont(font);

            Row header = sheet.createRow(0);
            for (int i = 0; i < TEMPLATE_HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(TEMPLATE_HEADERS[i]);
                cell.setCellStyle(headerStyle);
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate template", e);
        }
    }

    /** Parse uploaded Excel and create accounts. Returns result summary. */
    public Map<String, Object> importFromExcel(InputStream inputStream, User currentUser) {
        List<String> successes = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        try (Workbook wb = new XSSFWorkbook(inputStream)) {
            Sheet sheet = wb.getSheetAt(0);
            int lastRow = sheet.getLastRowNum();

            for (int i = 1; i <= lastRow; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    PlatformAccountCreateRequest req = parseRow(row);
                    platformAccountService.createAccount(req, currentUser);
                    successes.add("第" + (i + 1) + "行: " + req.getAccountName() + " 导入成功");
                } catch (RuntimeException e) {
                    failures.add("第" + (i + 1) + "行: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse Excel file", e);
        }

        return Map.of(
                "total", successes.size() + failures.size(),
                "success", successes.size(),
                "failed", failures.size(),
                "successDetails", successes,
                "failureDetails", failures
        );
    }

    private PlatformAccountCreateRequest parseRow(Row row) {
        String accountName = getCellString(row, 0);
        String typeStr = getCellString(row, 1);
        String username = getCellString(row, 2);
        String password = getCellString(row, 3);
        String url = getCellString(row, 4);
        String contactPerson = getCellString(row, 5);
        String contactPhone = getCellString(row, 6);
        String contactEmail = getCellString(row, 7);
        String remarks = getCellString(row, 8);

        if (accountName == null || accountName.isBlank()) {
            throw new IllegalArgumentException("平台名称不能为空");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("登录账号不能为空");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("登录密码不能为空");
        }

        PlatformType platformType = TYPE_MAP.get(typeStr);
        if (platformType == null) {
            // Try enum name directly
            try {
                platformType = PlatformType.valueOf(typeStr != null ? typeStr.toUpperCase() : "");
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("不支持的平台类型: " + typeStr + "（可选: 政府采购网/招投标平台/建设工程平台/其他）");
            }
        }

        return PlatformAccountCreateRequest.builder()
                .accountName(accountName)
                .platformType(platformType)
                .username(username)
                .password(password)
                .url(url)
                .contactPerson(contactPerson)
                .contactPhone(contactPhone)
                .contactEmail(contactEmail)
                .hasCa(false)
                .remarks(remarks)
                .build();
    }

    private String getCellString(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
    }
}
