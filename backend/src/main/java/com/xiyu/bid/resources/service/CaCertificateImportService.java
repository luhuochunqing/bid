package com.xiyu.bid.resources.service;

import com.xiyu.bid.resources.dto.CaCertificateRequest;
import com.xiyu.bid.common.util.ExcelAutoSizeHelper;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CA certificate Excel batch import service.
 * @deprecated 已被 {@link CaCertificateImportAppService} 取代（异步任务模式）。
 *             保留仅供历史参考，新功能请使用新服务。
 */
@Deprecated
@Service
@RequiredArgsConstructor
@Slf4j
public class CaCertificateImportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String[] TEMPLATE_HEADERS = {
            "CA类型*", "印章类型*", "持有人*", "保管员姓名*",
            "有效期至(yyyy-MM-dd)*", "颁发机构", "电子账号",
            "CA密码", "平台URL", "关联平台ID", "备注"
    };
    private static final Map<String, String> CA_TYPE_MAP = Map.of(
            "实体CA", "ENTITY_CA", "电子CA", "ELECTRONIC_CA");
    private static final Map<String, String> SEAL_TYPE_MAP = Map.of(
            "公章", "OFFICIAL_SEAL", "法人章", "LEGAL_PERSON_SEAL",
            "法人签字", "LEGAL_SIGN", "联系人签字", "CONTACT_SIGN");

    private final CaCertificateService caCertificateService;

    /** Generate template .xlsx bytes. */
    public byte[] generateTemplate() {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("CA证书导入模板");
            CellStyle headerStyle = wb.createCellStyle();
            Font font = wb.createFont();
            font.setBold(true);
            headerStyle.setFont(font);

            Row header = sheet.createRow(0);
            for (int i = 0; i < TEMPLATE_HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(TEMPLATE_HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }
            ExcelAutoSizeHelper.autoSizeColumns(sheet, TEMPLATE_HEADERS.length);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate template", e);
        }
    }

    /** Parse uploaded Excel and create CA certificates. Returns result summary. */
    public Map<String, Object> importFromExcel(InputStream inputStream) {
        List<String> successes = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        try (Workbook wb = new XSSFWorkbook(inputStream)) {
            Sheet sheet = wb.getSheetAt(0);
            int lastRow = sheet.getLastRowNum();

            for (int i = 1; i <= lastRow; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    CaCertificateRequest req = parseRow(row);
                    caCertificateService.create(req);
                    successes.add("第" + (i + 1) + "行: " + req.getHolderName() + " 导入成功");
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

    private CaCertificateRequest parseRow(Row row) {
        String caTypeStr = getCellString(row, 0);
        String sealTypeStr = getCellString(row, 1);
        String holderName = getCellString(row, 2);
        String custodianName = getCellString(row, 3);
        String expiryDateStr = getCellString(row, 4);
        String issuer = getCellString(row, 5);
        String electronicAccount = getCellString(row, 6);
        String caPassword = getCellString(row, 7);
        String caPlatformUrl = getCellString(row, 8);
        String platformIdsRaw = getCellString(row, 9);
        String remarks = getCellString(row, 10);

        java.util.List<Long> platformIds = parsePlatformIds(platformIdsRaw);

        if (holderName == null || holderName.isBlank()) {
            throw new IllegalArgumentException("持有人不能为空");
        }
        if (custodianName == null || custodianName.isBlank()) {
            throw new IllegalArgumentException("保管员姓名不能为空");
        }

        String caType = CA_TYPE_MAP.getOrDefault(caTypeStr, caTypeStr);
        if (caType == null || caType.isBlank()) {
            throw new IllegalArgumentException("CA类型不能为空（可选: 实体CA/电子CA）");
        }
        String sealType = SEAL_TYPE_MAP.getOrDefault(sealTypeStr, sealTypeStr);
        if (sealType == null || sealType.isBlank()) {
            throw new IllegalArgumentException("印章类型不能为空（可选: 公章/法人章/法人签字/联系人签字）");
        }

        LocalDate expiryDate = null;
        if (expiryDateStr != null && !expiryDateStr.isBlank()) {
            try {
                expiryDate = LocalDate.parse(expiryDateStr, DATE_FMT);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("有效期格式错误，请使用 yyyy-MM-dd 格式");
            }
        } else {
            throw new IllegalArgumentException("有效期至不能为空");
        }

        CaCertificateRequest req = new CaCertificateRequest();
        req.setCaType(caType);
        req.setSealType(sealType);
        req.setHolderName(holderName);
        req.setCustodianName(custodianName);
        req.setCustodianId(0L); // 需要手动绑定保管员ID
        req.setExpiryDate(expiryDate);
        req.setIssuer(issuer);
        req.setElectronicAccount(electronicAccount);
        req.setCaPassword(caPassword);
        req.setCaPlatformUrl(caPlatformUrl);
        req.setPlatformIds(platformIds);
        req.setRemarks(remarks);
        return req;
    }

    /**
     * Parse a comma / semicolon separated cell into a list of Long IDs.
     * Skips empty tokens and tokens that are not pure decimal numbers
     * (mirrors the back-fill logic in V1073).
     */
    private static java.util.List<Long> parsePlatformIds(String raw) {
        if (raw == null || raw.isBlank()) return java.util.Collections.emptyList();
        java.util.List<Long> out = new java.util.ArrayList<>();
        for (String token : raw.split("[,;，；\\s]+")) {
            String t = token.trim();
            if (t.isEmpty()) continue;
            if (!t.matches("^[0-9]+$")) continue;
            try {
                out.add(Long.parseLong(t));
            } catch (NumberFormatException ignored) { log.debug("Skipping non-numeric ID: {}", t); }
        }
        return out;
    }

    private String getCellString(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double val = cell.getNumericCellValue();
                if (val == Math.floor(val) && !Double.isInfinite(val)) {
                    yield String.valueOf((long) val);
                }
                yield String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
    }
}
