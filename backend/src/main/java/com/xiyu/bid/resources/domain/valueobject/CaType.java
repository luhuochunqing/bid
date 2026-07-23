package com.xiyu.bid.resources.domain.valueobject;

import java.util.Map;

/**
 * CA 类型枚举（纯核心，统一 code ↔ 中文标签 映射）。
 *
 * <p>消除 {@code CaCertificateExportService}、{@code CaCertificateImportService}、
 * 通知文案中散落的 caType 映射逻辑。
 *
 * <p>FP-Java 合规：无 Spring 注解、无 IO、无副作用。
 */
public enum CaType {

    ENTITY_CA("实体CA"),
    ELECTRONIC_CA("电子CA");

    private static final Map<String, CaType> CODE_INDEX = Map.of(
            "ENTITY_CA", ENTITY_CA,
            "ELECTRONIC_CA", ELECTRONIC_CA);

    private final String label;

    CaType(String label) {
        this.label = label;
    }

    public String code() {
        return name();
    }

    public String label() {
        return label;
    }

    /**
     * 按 code 解析枚举，未知 code 返回 null。
     *
     * @param code ca_type 字段值（如 "ENTITY_CA"）
     * @return 枚举值或 null
     */
    public static CaType fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return CODE_INDEX.get(code);
    }

    /**
     * 按 code 解析中文标签，未知 code 原样返回。
     *
     * <p>用于通知文案等场景：code 为 null/空时返回"未知"，
     * code 不在已知枚举内时原样返回（兼容历史脏数据，不丢失信息）。
     *
     * @param code ca_type 字段值
     * @return 中文标签（如"实体CA"）或"未知"或原 code
     */
    public static String labelOf(String code) {
        CaType type = fromCode(code);
        if (type != null) {
            return type.label;
        }
        if (code == null || code.isBlank()) {
            return "未知";
        }
        return code;
    }
}
