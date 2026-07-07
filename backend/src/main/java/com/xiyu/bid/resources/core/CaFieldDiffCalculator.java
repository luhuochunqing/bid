// Input: 两个 CaCertificateEntity 快照（变更前/后）
// Output: 字段变更摘要（格式："字段名：旧值 -> 新值；字段名：旧值 -> 新值"）
// Pos: 纯核心 / 领域规则层（FP-Java：不依赖框架，可单测）
// 维护声明: CaCertificateEntity 字段增减时同步更新 FIELD_LABELS 和 diff 逻辑.
package com.xiyu.bid.resources.core;

import com.xiyu.bid.resources.entity.CaCertificateEntity;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * CO-515: CA 证书编辑字段 diff 计算器（纯核心）。
 *
 * <p>对比变更前后的 CaCertificateEntity，生成审计日志详情摘要。
 * 输出格式："字段名：旧值 -> 新值；字段名：旧值 -> 新值"
 *
 * <p>设计要点：
 * <ul>
 *   <li>不依赖 Spring/JPA 等框架，可独立单元测试（FP-Java Contract）</li>
 *   <li>字段标签使用中文，与 CO-515 需求"变更字段"展示一致</li>
 *   <li>敏感字段（caPassword）不展示实际值，变更时统一显示"已更新"</li>
 *   <li>platformIds 由 Service 层单独处理（需查关联表），本类只处理实体本身字段</li>
 * </ul>
 */
public final class CaFieldDiffCalculator {

    /** CO-515: 实体字段 → 中文标签映射（顺序敏感，LinkedHashMap 保序） */
    private static final Map<String, String> FIELD_LABELS = new LinkedHashMap<>();

    static {
        FIELD_LABELS.put("caType", "CA类型");
        FIELD_LABELS.put("sealType", "印章类型");
        FIELD_LABELS.put("electronicAccount", "电子账号");
        FIELD_LABELS.put("caPassword", "CA密码");
        FIELD_LABELS.put("issuer", "颁发机构");
        FIELD_LABELS.put("holderName", "持有人");
        FIELD_LABELS.put("expiryDate", "有效期至");
        FIELD_LABELS.put("caPlatformUrl", "平台地址/APP");
        FIELD_LABELS.put("custodianId", "保管员");
        FIELD_LABELS.put("custodianName", "保管员姓名");
        FIELD_LABELS.put("remarks", "备注");
    }

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private CaFieldDiffCalculator() {
    }

    /**
     * 计算两个 CA 证书实体的字段变更摘要。
     *
     * @param before 变更前快照（null 时视为全部字段从空变更为 after 值）
     * @param after  变更后快照（null 时返回空列表）
     * @return 变更项列表，每项格式"字段名：旧值 -> 新值"；无变更返回空列表
     */
    public static List<String> diff(CaCertificateEntity before, CaCertificateEntity after) {
        if (after == null) {
            return List.of();
        }
        if (before == null) {
            before = blankEntity();
        }

        List<String> changes = new ArrayList<>();
        // caType
        if (!Objects.equals(before.getCaType(), after.getCaType())) {
            changes.add(format("caType", before.getCaType(), after.getCaType()));
        }
        // sealType
        if (!Objects.equals(before.getSealType(), after.getSealType())) {
            changes.add(format("sealType", before.getSealType(), after.getSealType()));
        }
        // electronicAccount
        if (!Objects.equals(before.getElectronicAccount(), after.getElectronicAccount())) {
            changes.add(format("electronicAccount", before.getElectronicAccount(), after.getElectronicAccount()));
        }
        // caPassword — 敏感字段，不展示实际值
        if (!Objects.equals(before.getCaPassword(), after.getCaPassword())) {
            changes.add(formatPassword());
        }
        // issuer
        if (!Objects.equals(before.getIssuer(), after.getIssuer())) {
            changes.add(format("issuer", before.getIssuer(), after.getIssuer()));
        }
        // holderName
        if (!Objects.equals(before.getHolderName(), after.getHolderName())) {
            changes.add(format("holderName", before.getHolderName(), after.getHolderName()));
        }
        // expiryDate
        if (!Objects.equals(before.getExpiryDate(), after.getExpiryDate())) {
            changes.add(formatDate("expiryDate", before.getExpiryDate(), after.getExpiryDate()));
        }
        // caPlatformUrl
        if (!Objects.equals(before.getCaPlatformUrl(), after.getCaPlatformUrl())) {
            changes.add(format("caPlatformUrl", before.getCaPlatformUrl(), after.getCaPlatformUrl()));
        }
        // custodianId
        if (!Objects.equals(before.getCustodianId(), after.getCustodianId())) {
            changes.add(format("custodianId", before.getCustodianId(), after.getCustodianId()));
        }
        // custodianName
        if (!Objects.equals(before.getCustodianName(), after.getCustodianName())) {
            changes.add(format("custodianName", before.getCustodianName(), after.getCustodianName()));
        }
        // remarks
        if (!Objects.equals(before.getRemarks(), after.getRemarks())) {
            changes.add(format("remarks", before.getRemarks(), after.getRemarks()));
        }
        return changes;
    }

    /**
     * 将变更项列表格式化为单行摘要字符串。
     * 例："CA类型：ENTITY_CA -> ELECTRONIC_CA；保管员：张三 -> 李四"
     */
    public static String formatSummary(List<String> changes) {
        if (changes == null || changes.isEmpty()) {
            return "";
        }
        return String.join("；", changes);
    }

    private static String format(String fieldName, Object oldValue, Object newValue) {
        String label = FIELD_LABELS.getOrDefault(fieldName, fieldName);
        return label + "：" + displayValue(oldValue) + " -> " + displayValue(newValue);
    }

    private static String formatDate(String fieldName, LocalDate oldValue, LocalDate newValue) {
        String label = FIELD_LABELS.getOrDefault(fieldName, fieldName);
        return label + "：" + displayDate(oldValue) + " -> " + displayDate(newValue);
    }

    private static String formatPassword() {
        return "CA密码：已更新";
    }

    private static String displayValue(Object value) {
        if (value == null) return "-";
        String str = String.valueOf(value);
        return str.isEmpty() ? "-" : str;
    }

    private static String displayDate(LocalDate value) {
        if (value == null) return "-";
        return value.format(DATE_FORMATTER);
    }

    private static CaCertificateEntity blankEntity() {
        return CaCertificateEntity.builder().build();
    }
}
