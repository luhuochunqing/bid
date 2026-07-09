package com.xiyu.bid.platform.domain;

import com.xiyu.bid.platform.entity.PlatformAccount;

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
            "账号保管员工号*", "是否有CA", "备注",
            "注册人", "注册手机", "注册邮箱"
    };

    public static final int COL_ACCOUNT_NAME = 0;
    public static final int COL_URL = 1;
    public static final int COL_USERNAME = 2;
    public static final int COL_PASSWORD = 3;
    public static final int COL_CONTACT_PERSON = 4;
    public static final int COL_HAS_CA = 5;
    public static final int COL_REMARKS = 6;
    public static final int COL_REGISTRANT = 7;
    public static final int COL_REGISTER_PHONE = 8;
    public static final int COL_REGISTER_EMAIL = 9;

    /** 字段长度上限（CO-560：行级校验，超长不触发 DB 异常）。
     *  数值须与 {@link PlatformAccount} 实体 @Column(length=...) 保持一致。 */
    public static final int LEN_REGISTRANT = PlatformAccount.LEN_REGISTRANT;
    public static final int LEN_REGISTER_PHONE = 20;
    public static final int LEN_REGISTER_EMAIL = 200;
    public static final int LEN_REMARKS = 500;

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

        // Field length validation (CO-560: 超长在此行级失败，不触发 DB 异常)
        validateLength(registrant, LEN_REGISTRANT, "注册人", errors);
        validateLength(registerPhone, LEN_REGISTER_PHONE, "注册手机", errors);
        validateLength(registerEmail, LEN_REGISTER_EMAIL, "注册邮箱", errors);
        validateLength(remarks, LEN_REMARKS, "备注", errors);

        // hasCa parsing
        Boolean hasCa = parseBoolean(hasCaStr);

        // Contact person employee number
        String employeeNumber = cellAt(cells, COL_CONTACT_PERSON).trim();
        if (employeeNumber.isEmpty()) {
            errors.add("请填入账号保管员工号");
        }

        return new ParsedAccountRow(rowIndex, accountName, url, username, password,
                employeeNumber, registrant, registerPhone, registerEmail,
                hasCa, remarks, errors);
    }

    private static Boolean parseBoolean(String value) {
        if (value == null || value.isBlank()) return false;
        String lower = value.trim().toLowerCase();
        return "是".equals(value) || "true".equals(lower) || "yes".equals(lower) || "1".equals(value);
    }

    private static String cellAt(String[] cells, int index) {
        return index < cells.length && cells[index] != null ? cells[index] : "";
    }

    /** 字段长度校验：超长则加入 errors（行级失败，不触发 DB 异常）。 */
    private static void validateLength(String value, int maxLength, String fieldLabel, List<String> errors) {
        if (value != null && value.length() > maxLength) {
            errors.add(fieldLabel + "长度超过" + maxLength + "字符（当前" + value.length() + "字符）");
        }
    }

    /** 解析后的一行数据 */
    public record ParsedAccountRow(
            int rowIndex,
            String accountName, String url, String username, String password,
            String employeeNumber, String registrant,
            String registerPhone, String registerEmail, Boolean hasCa, String remarks,
            List<String> errors
    ) {
        public boolean valid() { return errors == null || errors.isEmpty(); }
    }
}
