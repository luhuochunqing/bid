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
            "平台类型", "账号保管员工号*", "是否有CA", "备注",
            "注册人", "注册手机", "注册邮箱"
    };

    public static final int COL_ACCOUNT_NAME = 0;
    public static final int COL_URL = 1;
    public static final int COL_USERNAME = 2;
    public static final int COL_PASSWORD = 3;
    public static final int COL_PLATFORM_TYPE = 4;
    public static final int COL_CONTACT_PERSON = 5;
    public static final int COL_HAS_CA = 6;
    public static final int COL_REMARKS = 7;
    public static final int COL_REGISTRANT = 8;
    public static final int COL_REGISTER_PHONE = 9;
    public static final int COL_REGISTER_EMAIL = 10;

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
        String hasCaStr = cellAt(cells, COL_HAS_CA).trim();
        String remarks = cellAt(cells, COL_REMARKS).trim();
        String registrant = cellAt(cells, COL_REGISTRANT).trim();
        String registerPhone = cellAt(cells, COL_REGISTER_PHONE).trim();
        String registerEmail = cellAt(cells, COL_REGISTER_EMAIL).trim();

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

        // Contact person employee number
        String employeeNumber = cellAt(cells, COL_CONTACT_PERSON).trim();
        if (employeeNumber.isEmpty()) {
            errors.add("请填入账号保管员工号");
        }

        return new ParsedAccountRow(rowIndex, accountName, url, username, password,
                platformType, employeeNumber, registrant, registerPhone, registerEmail,
                hasCa, remarks, errors);
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

    private static String cellAt(String[] cells, int index) {
        return index < cells.length && cells[index] != null ? cells[index] : "";
    }

    /** 解析后的一行数据 */
    public record ParsedAccountRow(
            int rowIndex,
            String accountName, String url, String username, String password,
            PlatformType platformType, String employeeNumber, String registrant,
            String registerPhone, String registerEmail, Boolean hasCa, String remarks,
            List<String> errors
    ) {
        public boolean valid() { return errors == null || errors.isEmpty(); }
    }
}
