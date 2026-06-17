package com.xiyu.bid.warehouse.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 纯核心：仓库导入策略 — 模板列定义、字段解析、行级校验、附件命名规范。
 * 不含 I/O、不含副作用、不修改入参。
 */
public class WarehouseImportPolicy {

    public static final String[] TEMPLATE_HEADERS = {
            "仓库名称", "仓库类型", "省份", "地址", "面积", "区域", "联系人", "备注",
            "开始时间", "结束时间",
            "出租方", "承租方", "发票租期起", "发票租期止", "关仓计划",
            "是否有产权证", "产权证附件",
            "是否有发票", "发票附件",
            "是否有仓库照片", "照片附件",
            "资料备注"
    };

    public static final int COL_NAME = 0;
    public static final int COL_TYPE = 1;
    public static final int COL_PROVINCE = 2;
    public static final int COL_ADDRESS = 3;
    public static final int COL_AREA = 4;
    public static final int COL_REGION = 5;
    public static final int COL_CONTACT = 6;
    public static final int COL_REMARKS = 7;
    public static final int COL_START_DATE = 8;
    public static final int COL_END_DATE = 9;
    public static final int COL_LESSOR = 10;
    public static final int COL_LESSEE = 11;
    public static final int COL_INVOICE_START = 12;
    public static final int COL_INVOICE_END = 13;
    public static final int COL_CLOSE_PLAN = 14;
    public static final int COL_HAS_PROPERTY_CERT = 15;
    public static final int COL_PROPERTY_CERT_FILE = 16;
    public static final int COL_HAS_INVOICE = 17;
    public static final int COL_INVOICE_FILE = 18;
    public static final int COL_HAS_PHOTOS = 19;
    public static final int COL_PHOTOS_FILE = 20;
    public static final int COL_CERT_REMARKS = 21;

    public static final int EXPECTED_COL_COUNT = 22;

    private static final Pattern SPECIAL_CHARS = Pattern.compile("[/\\\\:*?\"<>|]");
    private static final Set<String> TYPE_VALUES = Set.of("自营", "云仓");
    private static final Set<String> YES_VALUES = Set.of("是", "yes", "Y", "y", "1", "true");
    private static final Set<String> NO_VALUES = Set.of("否", "no", "N", "n", "0", "false", "");

    private WarehouseImportPolicy() {
    }

    /**
     * 净化仓库名称：将特殊字符（/ \ : * ? " < > |）替换为下划线。
     */
    public static String sanitizeWarehouseName(String name) {
        if (name == null) return "";
        return SPECIAL_CHARS.matcher(name).replaceAll("_").trim();
    }

    /**
     * 校验表头是否符合模板定义。
     */
    public static List<String> validateHeader(String[] actualHeader) {
        List<String> errors = new ArrayList<>();
        if (actualHeader == null || actualHeader.length < EXPECTED_COL_COUNT) {
            errors.add("模板列数不足：期望 " + EXPECTED_COL_COUNT + " 列，实际 " + (actualHeader == null ? 0 : actualHeader.length) + " 列");
            return errors;
        }
        for (int i = 0; i < EXPECTED_COL_COUNT; i++) {
            String expected = TEMPLATE_HEADERS[i];
            String actual = actualHeader[i] == null ? "" : actualHeader[i].trim();
            if (!expected.equals(actual)) {
                errors.add("第 " + (i + 1) + " 列表头不匹配：期望 \"" + expected + "\"，实际 \"" + actual + "\"");
            }
        }
        return errors;
    }

    /**
     * 将 Excel 行解析为校验后的仓库草稿 + 校验错误列表。
     * 全部失败信息以值返回，不抛业务异常。
     */
    public static ParsedRow parseRow(int rowIndex, String[] cells) {
        List<String> errors = new ArrayList<>();
        ParsedRow result = new ParsedRow();
        result.rowIndex = rowIndex;
        result.rawCells = cells;

        if (cells == null || cells.length == 0) {
            errors.add("空行");
            result.errors = errors;
            return result;
        }

        String rawName = stringAt(cells, COL_NAME).trim();
        if (rawName.isEmpty()) {
            errors.add("仓库名称不能为空");
        }
        String sanitizedName = sanitizeWarehouseName(rawName);
        result.sanitizedName = sanitizedName;

        String typeText = stringAt(cells, COL_TYPE).trim();
        WarehouseType type = null;
        if (typeText.isEmpty()) {
            errors.add("仓库类型不能为空");
        } else if (!TYPE_VALUES.contains(typeText)) {
            errors.add("仓库类型必须是「自营」或「云仓」，实际: " + typeText);
        } else {
            type = "自营".equals(typeText) ? WarehouseType.SELF_OPERATED : WarehouseType.CLOUD;
        }
        result.type = type;

        String province = stringAt(cells, COL_PROVINCE).trim();
        if (province.isEmpty()) errors.add("省份不能为空");
        result.province = province;

        String address = stringAt(cells, COL_ADDRESS).trim();
        if (address.isEmpty()) errors.add("地址不能为空");
        result.address = address;

        BigDecimal area = parseBigDecimal(stringAt(cells, COL_AREA), "面积", errors);
        result.area = area;

        String region = stringAt(cells, COL_REGION).trim();
        if (region.isEmpty()) errors.add("区域不能为空");
        result.region = region;

        String contact = stringAt(cells, COL_CONTACT).trim();
        if (contact.isEmpty()) errors.add("联系人不能为空");
        result.contactPerson = contact;

        result.remarks = stringAt(cells, COL_REMARKS).trim();

        LocalDate startDate = parseDate(stringAt(cells, COL_START_DATE), "开始时间", errors);
        LocalDate endDate = parseDate(stringAt(cells, COL_END_DATE), "结束时间", errors);
        if (startDate != null && endDate != null && !endDate.isAfter(startDate)) {
            errors.add("结束时间必须晚于开始时间");
        }
        result.startDate = startDate;
        result.endDate = endDate;

        String lessor = stringAt(cells, COL_LESSOR).trim();
        if (lessor.isEmpty()) errors.add("出租方不能为空");
        result.lessor = lessor;

        String lessee = stringAt(cells, COL_LESSEE).trim();
        if (lessee.isEmpty()) errors.add("承租方不能为空");
        result.lessee = lessee;

        result.invoicePeriodStart = parseDate(stringAt(cells, COL_INVOICE_START), "发票租期起", new ArrayList<>());
        result.invoicePeriodEnd = parseDate(stringAt(cells, COL_INVOICE_END), "发票租期止", new ArrayList<>());
        if (result.invoicePeriodStart != null && result.invoicePeriodEnd != null
                && result.invoicePeriodEnd.isBefore(result.invoicePeriodStart)) {
            errors.add("发票租期止不能早于发票租期起");
        }

        result.closePlan = stringAt(cells, COL_CLOSE_PLAN).trim();

        boolean hasPropertyCert = parseYesNo(stringAt(cells, COL_HAS_PROPERTY_CERT), "是否有产权证", errors, result);
        boolean hasInvoice = parseYesNo(stringAt(cells, COL_HAS_INVOICE), "是否有发票", errors, result);
        boolean hasPhotos = parseYesNo(stringAt(cells, COL_HAS_PHOTOS), "是否有仓库照片", errors, result);

        result.hasPropertyCert = hasPropertyCert;
        result.hasInvoice = hasInvoice;
        result.hasPhotos = hasPhotos;

        String propertyCertFile = stringAt(cells, COL_PROPERTY_CERT_FILE).trim();
        String invoiceFile = stringAt(cells, COL_INVOICE_FILE).trim();
        String photosFile = stringAt(cells, COL_PHOTOS_FILE).trim();
        result.propertyCertFile = propertyCertFile;
        result.invoiceFile = invoiceFile;
        result.photosFile = photosFile;

        if (hasPropertyCert && propertyCertFile.isEmpty()) {
            errors.add("产权证=是时，产权证附件不能为空");
        }
        if (hasInvoice && invoiceFile.isEmpty()) {
            errors.add("发票=是时，发票附件不能为空");
        }
        if (hasPhotos && photosFile.isEmpty()) {
            errors.add("仓库照片=是时，照片附件不能为空");
        }

        result.certRemarks = stringAt(cells, COL_CERT_REMARKS).trim();

        if (!propertyCertFile.isEmpty()) {
            result.propertyCertExpectedName = buildAttachmentExpectedName(sanitizedName, "产权证", propertyCertFile);
        }
        if (!invoiceFile.isEmpty()) {
            result.invoiceExpectedName = buildAttachmentExpectedName(sanitizedName, "发票", invoiceFile);
        }
        if (!photosFile.isEmpty()) {
            result.photosExpectedName = buildAttachmentExpectedName(sanitizedName, "内外照片", photosFile);
        }

        result.errors = errors;
        return result;
    }

    /**
     * 将附件用户文件名转换为符合 WH_{仓库名称}_{附件类型}.{扩展名} 规范的标准化名称。
     */
    public static String buildAttachmentExpectedName(String sanitizedWarehouseName, String attachType, String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) return "";
        int dotIdx = originalFilename.lastIndexOf('.');
        String ext = (dotIdx >= 0 && dotIdx < originalFilename.length() - 1)
                ? originalFilename.substring(dotIdx) : "";
        return "WH_" + sanitizedWarehouseName + "_" + attachType + ext;
    }

    private static String stringAt(String[] cells, int idx) {
        if (cells == null || idx >= cells.length) return "";
        return cells[idx] == null ? "" : cells[idx];
    }

    private static BigDecimal parseBigDecimal(String text, String fieldName, List<String> errors) {
        if (text == null || text.isBlank()) {
            errors.add(fieldName + "不能为空");
            return null;
        }
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            errors.add(fieldName + "格式错误: " + text);
            return null;
        }
    }

    private static LocalDate parseDate(String text, String fieldName, List<String> errors) {
        if (text == null || text.isBlank()) {
            errors.add(fieldName + "不能为空");
            return null;
        }
        String trimmed = text.trim();
        try {
            return LocalDate.parse(trimmed);
        } catch (DateTimeParseException e) {
            try {
                return LocalDate.parse(trimmed.replace("/", "-"));
            } catch (DateTimeParseException ex) {
                errors.add(fieldName + "格式错误（应为 YYYY-MM-DD）: " + text);
                return null;
            }
        }
    }

    private static boolean parseYesNo(String text, String fieldName, List<String> errors, ParsedRow result) {
        if (text == null) text = "";
        String trimmed = text.trim();
        if (YES_VALUES.contains(trimmed)) return true;
        if (NO_VALUES.contains(trimmed)) return false;
        errors.add(fieldName + "必须是「是」或「否」，实际: " + text);
        return false;
    }

    public static class ParsedRow {
        public int rowIndex;
        public String[] rawCells;
        public String sanitizedName;
        public WarehouseType type;
        public String province;
        public String address;
        public BigDecimal area;
        public String region;
        public String contactPerson;
        public String remarks;
        public LocalDate startDate;
        public LocalDate endDate;
        public String lessor;
        public String lessee;
        public LocalDate invoicePeriodStart;
        public LocalDate invoicePeriodEnd;
        public String closePlan;
        public boolean hasPropertyCert;
        public boolean hasInvoice;
        public boolean hasPhotos;
        public String propertyCertFile;
        public String invoiceFile;
        public String photosFile;
        public String propertyCertExpectedName;
        public String invoiceExpectedName;
        public String photosExpectedName;
        public String certRemarks;
        public List<String> errors;
        public boolean valid() { return errors == null || errors.isEmpty(); }
    }
}
