package com.xiyu.bid.platform.domain;

import com.xiyu.bid.platform.entity.PlatformAccount.PlatformType;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 纯核心：平台账户导入策略 — 模板列定义、字段解析、行级校验。
 * 不含 I/O、不含副作用。
 */
public class PlatformAccountImportPolicy {

    public static final String[] HEADERS = {
            "平台名称*", "平台网址*", "登录账号*", "登录密码*",
            "平台类型", "联系人", "联系电话", "联系邮箱",
            "是否有CA", "CA保管员ID", "账号保管员ID", "备注"
    };

    public static final int COL_ACCOUNT_NAME = 0;
    public static final int COL_URL = 1;
    public static final int COL_USERNAME = 2;
    public static final int COL_PASSWORD = 3;
    public static final int COL_PLATFORM_TYPE = 4;
    public static final int COL_CONTACT_PERSON = 5;
    public static final int COL_CONTACT_PHONE = 6;
    public static final int COL_CONTACT_EMAIL = 7;
    public static final int COL_HAS_CA = 8;
    public static final int COL_CA_CUSTODIAN = 9;
    public static final int COL_CUSTODIAN = 10;
    public static final int COL_REMARKS = 11;

    private PlatformAccountImportPolicy() {}

    /** 校验表头列数与内容是否匹配 */
    public static List<String> validateHeader(String[] actualHeader) {
        List<String> errors = new ArrayList<>();
        if (actualHeader == null || actualHeader.length < HEADERS.length) {
            errors.add("表头列数不足：期望 " + HEADERS.length + " 列，实际 " +
                    (actualHeader == null ? 0 : actualHeader.length) + " 列");
            return errors;
        }
        for (int i = 0; i < HEADERS.length; i++) {
            String expected = normalizeHeader(HEADERS[i]);
            String actual = i < actualHeader.length ? normalizeHeader(actualHeader[i]) : "";
            if (!expected.equals(actual)) {
                errors.add("第 " + (i + 1) + " 列表头不匹配：期望 \"" + HEADERS[i] +
                        "\"，实际 \"" + (i < actualHeader.length ? actualHeader[i] : "(缺失)") + "\"");
            }
        }
        return errors;
    }

    private static String normalizeHeader(String raw) {
        if (raw == null) return "";
        return raw.replace(" ", "").replace("　", "").replace("*", "");
    }

    /** 解析一行平台账户数据 */
    public static ParsedAccountRow parseRow(int rowIndex, String[] cells) {
        List<String> errors = new ArrayList<>();

        String accountName = cellAt(cells, COL_ACCOUNT_NAME).trim();
        String url = cellAt(cells, COL_URL).trim();
        String username = cellAt(cells, COL_USERNAME).trim();
        String password = cellAt(cells, COL_PASSWORD).trim();
        String platformTypeStr = cellAt(cells, COL_PLATFORM_TYPE).trim();
        String contactPerson = cellAt(cells, COL_CONTACT_PERSON).trim();
        String contactPhone = cellAt(cells, COL_CONTACT_PHONE).trim();
        String contactEmail = cellAt(cells, COL_CONTACT_EMAIL).trim();
        String hasCaStr = cellAt(cells, COL_HAS_CA).trim();
        String caCustodianStr = cellAt(cells, COL_CA_CUSTODIAN).trim();
        String custodianStr = cellAt(cells, COL_CUSTODIAN).trim();
        String remarks = cellAt(cells, COL_REMARKS).trim();

        // Required field validation
        if (accountName.isEmpty()) errors.add("平台名称不能为空");
        if (url.isEmpty()) errors.add("平台网址不能为空");
        if (username.isEmpty()) errors.add("登录账号不能为空");
        if (password.isEmpty()) errors.add("登录密码不能为空");

        // Platform type parsing
        PlatformType platformType = PlatformType.OTHER;
        if (!platformTypeStr.isEmpty()) {
            platformType = parsePlatformType(platformTypeStr, errors);
        }

        // hasCa parsing
        Boolean hasCa = parseBoolean(hasCaStr);

        // Custodian ID parsing
        Long caCustodian = parseLongOrNull(caCustodianStr);
        if (!caCustodianStr.isEmpty() && caCustodian == null) {
            errors.add("CA保管员ID格式错误，需为数字");
        }
        Long custodian = parseLongOrNull(custodianStr);
        if (!custodianStr.isEmpty() && custodian == null) {
            errors.add("账号保管员ID格式错误，需为数字");
        }

        return new ParsedAccountRow(rowIndex, accountName, url, username, password,
                platformType, contactPerson, contactPhone, contactEmail,
                hasCa, caCustodian, custodian, remarks, errors);
    }

    private static PlatformType parsePlatformType(String value, List<String> errors) {
        return switch (value) {
            case "政府采购网" -> PlatformType.GOV_PROCUREMENT;
            case "招投标平台" -> PlatformType.BIDDING_PLATFORM;
            case "建设工程平台" -> PlatformType.CONSTRUCTION_PLATFORM;
            case "其他" -> PlatformType.OTHER;
            default -> {
                errors.add("平台类型「" + value + "」不合法，可选：政府采购网/招投标平台/建设工程平台/其他");
                yield PlatformType.OTHER;
            }
        };
    }

    private static Boolean parseBoolean(String value) {
        if (value == null || value.isBlank()) return false;
        String lower = value.trim().toLowerCase();
        return "是".equals(value) || "true".equals(lower) || "yes".equals(lower) || "1".equals(value);
    }

    private static Long parseLongOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String cellAt(String[] cells, int index) {
        return index < cells.length && cells[index] != null ? cells[index] : "";
    }

    /** 解析后的一行数据 */
    public record ParsedAccountRow(
            int rowIndex,
            String accountName, String url, String username, String password,
            PlatformType platformType, String contactPerson, String contactPhone,
            String contactEmail, Boolean hasCa, Long caCustodian,
            Long custodian, String remarks,
            List<String> errors
    ) {
        public boolean valid() { return errors == null || errors.isEmpty(); }
    }
}
