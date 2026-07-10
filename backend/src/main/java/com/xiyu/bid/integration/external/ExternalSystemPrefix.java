// Input: 外部系统来源标识
// Output: externalId 前缀常量与解析工具
// Pos: integration/external/ - 外部系统集成工具
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.integration.external;

/**
 * 外部系统来源前缀枚举。
 * <p>externalId 格式为 "{sourceSystem}:{sourceId}"，本枚举统一维护 sourceSystem 部分，
 * 避免在业务代码中硬编码 "CRM:" 等前缀（CO-152 / CO-277 设计修正）。
 *
 * @see ExternalIdParser
 */
public enum ExternalSystemPrefix {

    CRM("CRM");

    private final String code;

    ExternalSystemPrefix(String code) {
        this.code = code;
    }

    /**
     * 返回来源系统代码（不含冒号）。
     */
    public String code() {
        return code;
    }

    /**
     * 返回 externalId 前缀（含冒号），例如 "CRM:"。
     */
    public String prefix() {
        return code + ":";
    }

    /**
     * 将 sourceId 格式化为完整的 externalId。
     */
    public String formatExternalId(String sourceId) {
        return prefix() + sourceId;
    }

    /**
     * 判断 externalId 的来源系统是否为本枚举值（大小写不敏感）。
     */
    public boolean matches(String externalId) {
        if (externalId == null) {
            return false;
        }
        int idx = externalId.indexOf(':');
        if (idx <= 0) {
            return false;
        }
        String system = externalId.substring(0, idx);
        return this.code.equalsIgnoreCase(system);
    }

    /**
     * 从匹配的 externalId 中提取 sourceId 部分。
     * <p>不匹配时返回 {@code null}；匹配但 sourceId 为空时返回空字符串。
     * <p>复用 {@link ExternalIdParser#extractSourceId}，避免解析逻辑分叉。
     */
    public String extractSourceId(String externalId) {
        if (!matches(externalId)) {
            return null;
        }
        String sourceId = ExternalIdParser.extractSourceId(externalId);
        return sourceId.isEmpty() ? "" : sourceId;
    }
}
