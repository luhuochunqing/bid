package com.xiyu.bid.resources.domain;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * 纯核心：CA证书导入策略 — 模板列定义、字段解析、行级校验。
 * 不含 I/O、不含副作用。
 */
public class CaCertificateImportPolicy {

    public static final String[] HEADERS = {
            "CA类型*", "印章类型*", "持有人", "保管员*", "有效期至*",
            "颁发机构", "电子账号", "CA密码",
            "平台地址/APP", "关联平台", "备注"
    };

    public static final int COL_CA_TYPE = 0;
    public static final int COL_SEAL_TYPE = 1;
    public static final int COL_HOLDER_NAME = 2;
    public static final int COL_CUSTODIAN_NAME = 3;
    public static final int COL_EXPIRY_DATE = 4;
    public static final int COL_ISSUER = 5;
    public static final int COL_ELECTRONIC_ACCOUNT = 6;
    public static final int COL_CA_PASSWORD = 7;
    public static final int COL_CA_PLATFORM_URL = 8;
    public static final int COL_PLATFORM_NAMES = 9;
    public static final int COL_REMARKS = 10;

    public static final String[] CA_TYPE_OPTIONS = {"实体CA", "电子CA"};
    public static final String[] SEAL_TYPE_OPTIONS = {"公章", "法人章", "法人签字", "联系人签字"};

    private static final Set<String> VALID_CA_TYPES = Set.of(CA_TYPE_OPTIONS);
    private static final Set<String> VALID_SEAL_TYPES = Set.of(SEAL_TYPE_OPTIONS);

    private CaCertificateImportPolicy() {}

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

    /** 解析一行 CA 证书数据 */
    public static ParsedCaRow parseRow(int rowIndex, String[] cells) {
        List<String> errors = new ArrayList<>();

        String caType = cellAt(cells, COL_CA_TYPE).trim();
        String sealType = cellAt(cells, COL_SEAL_TYPE).trim();
        String holderName = cellAt(cells, COL_HOLDER_NAME).trim();
        String custodianName = cellAt(cells, COL_CUSTODIAN_NAME).trim();
        String expiryDateStr = cellAt(cells, COL_EXPIRY_DATE).trim();
        String issuer = cellAt(cells, COL_ISSUER).trim();
        String electronicAccount = cellAt(cells, COL_ELECTRONIC_ACCOUNT).trim();
        String caPassword = cellAt(cells, COL_CA_PASSWORD).trim();
        String caPlatformUrl = cellAt(cells, COL_CA_PLATFORM_URL).trim();
        String platformNamesRaw = cellAt(cells, COL_PLATFORM_NAMES).trim();
        String remarks = cellAt(cells, COL_REMARKS).trim();

        // Required fields
        if (caType.isEmpty()) {
            errors.add("CA类型不能为空");
        } else if (!VALID_CA_TYPES.contains(caType)) {
            errors.add("CA类型必须是「实体CA」或「电子CA」");
        }
        if (sealType.isEmpty()) {
            errors.add("印章类型不能为空");
        } else if (!VALID_SEAL_TYPES.contains(sealType)) {
            errors.add("印章类型必须是：公章/法人章/法人签字/联系人签字");
        }
        if (custodianName.isEmpty()) errors.add("保管员不能为空");

        LocalDate expiryDate = null;
        if (expiryDateStr.isEmpty()) {
            errors.add("有效期至不能为空");
        } else {
            try {
                expiryDate = LocalDate.parse(expiryDateStr);
            } catch (DateTimeParseException e) {
                errors.add("有效期至格式错误，需为 yyyy-MM-dd");
            }
        }

        // Map CA type
        String caTypeCode = caType.equals("实体CA") ? "ENTITY_CA" :
                caType.equals("电子CA") ? "ELECTRONIC_CA" : caType;

        // Map seal type
        String sealTypeCode = switch (sealType) {
            case "公章" -> "OFFICIAL_SEAL";
            case "法人章" -> "LEGAL_PERSON_SEAL";
            case "法人签字" -> "LEGAL_SIGN";
            case "联系人签字" -> "CONTACT_SIGN";
            default -> sealType;
        };

        // 电子CA必须填写电子账号（与新增表单一致）
        if (caTypeCode.equals("ELECTRONIC_CA") && electronicAccount.isEmpty()) {
            errors.add("电子CA必须填写电子账号");
        }

        // Parse platform names
        List<String> platformNames = new ArrayList<>();
        if (!platformNamesRaw.isEmpty()) {
            platformNames = Arrays.stream(platformNamesRaw.split("[,，;；]"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }

        return new ParsedCaRow(rowIndex, caTypeCode, sealTypeCode, holderName, custodianName,
                expiryDate, issuer, electronicAccount, caPassword, caPlatformUrl,
                platformNames, remarks, errors);
    }

    private static String cellAt(String[] cells, int index) {
        return index < cells.length && cells[index] != null ? cells[index] : "";
    }

    /** 解析后的一行数据 */
    public record ParsedCaRow(
            int rowIndex,
            String caType, String sealType, String holderName, String custodianName,
            LocalDate expiryDate, String issuer, String electronicAccount,
            String caPassword, String caPlatformUrl,
            List<String> platformNames, String remarks,
            List<String> errors
    ) {
        public boolean valid() { return errors == null || errors.isEmpty(); }
    }
}
