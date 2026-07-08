package com.xiyu.bid.alerts.domain;

import java.util.Optional;

/**
 * relatedId 解析纯核心。
 *
 * <p>统一解析格式 {@code "EntityType:NumericId"}（单冒号，ID 为 1-18 位纯数字），
 * 供 {@link AlertMessagePolicy} 和 {@code AlertNotificationOrchestrator} 共用，
 * 消除两套解析实现的分歧。</p>
 *
 * <p>FP-Java Profile 合规：</p>
 * <ul>
 *   <li>纯静态方法，无 Spring 注解、无 Repository/Service 依赖、无 IO</li>
 *   <li>不抛异常：解析失败返回 {@link Optional#empty()}</li>
 *   <li>无 setter、无状态、无副作用</li>
 * </ul>
 */
public final class RelatedIdParser {

    /** 数值 ID 的合法字符集（1-18 位数字，避免 long 溢出）。 */
    private static final String NUMERIC_ID_PATTERN = "\\d{1,18}";

    /** relatedId 中实体类型与 ID 的分隔符。 */
    public static final String SEPARATOR = ":";

    private RelatedIdParser() {
        // 纯核心工具类，禁止实例化
    }

    /**
     * 解析 relatedId。
     *
     * @param relatedId 格式 {@code "EntityType:NumericId"}，可为 null/blank
     * @return 解析结果；解析失败返回 {@link Optional#empty()}
     */
    public static Optional<ParsedRelatedId> parse(String relatedId) {
        if (relatedId == null || relatedId.isBlank()) {
            return Optional.empty();
        }
        int separatorIdx = relatedId.indexOf(SEPARATOR);
        if (separatorIdx <= 0) {
            return Optional.empty();
        }
        String entityType = relatedId.substring(0, separatorIdx);
        String entityIdRaw = relatedId.substring(separatorIdx + 1);
        if (entityType.isBlank() || !entityIdRaw.matches(NUMERIC_ID_PATTERN)) {
            return Optional.empty();
        }
        // 仅允许单分隔符：原始串中再出现分隔符视为格式错误
        if (relatedId.indexOf(SEPARATOR, separatorIdx + 1) >= 0) {
            return Optional.empty();
        }
        return Optional.of(new ParsedRelatedId(entityType, Long.parseLong(entityIdRaw)));
    }

    /**
     * 解析 relatedId 中的实体类型前缀（如 "Tender"）。
     *
     * @param relatedId 可为 null
     * @return 实体类型；解析失败返回 {@link Optional#empty()}
     */
    public static Optional<String> parseEntityType(String relatedId) {
        return parse(relatedId).map(ParsedRelatedId::entityType);
    }

    /**
     * 解析 relatedId 中的数字 ID。
     *
     * @param relatedId 可为 null
     * @return 实体 ID；解析失败返回 {@link Optional#empty()}
     */
    public static Optional<Long> parseEntityId(String relatedId) {
        return parse(relatedId).map(ParsedRelatedId::entityId);
    }

    /**
     * 判断 relatedId 是否以指定实体类型开头。
     *
     * @param relatedId  可为 null
     * @param entityType 实体类型前缀（如 "Tender"）
     * @return true 表示 relatedId 格式合法且以指定类型开头
     */
    public static boolean isEntityType(String relatedId, String entityType) {
        return parse(relatedId)
                .map(parsed -> parsed.entityType().equals(entityType))
                .orElse(false);
    }

    /**
     * relatedId 解析结果值对象。
     *
     * @param entityType 实体类型（如 "Tender"、"Project"）
     * @param entityId   实体 ID
     */
    public record ParsedRelatedId(String entityType, Long entityId) {
    }
}
