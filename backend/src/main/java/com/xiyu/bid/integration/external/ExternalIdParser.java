// Input: externalId 字符串（格式 "{sourceSystem}:{sourceId}"）
// Output: 解析后的 sourceId 部分
// Pos: integration/external/ - 外部系统集成工具
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.integration.external;

/**
 * externalId 解析工具（§4.1/§4.2 回调共用）。
 * <p>externalId 格式为 "{sourceSystem}:{sourceId}"，与标讯推送接口的 sourceId 取值一致。
 * <p>提取 sourceId 用于回调载荷，避免在多处重复实现解析逻辑。
 */
public final class ExternalIdParser {

    private ExternalIdParser() {}

    /**
     * 从 externalId 提取 sourceId 部分（冒号后的内容）。
     * <p>无 externalId、无冒号或冒号后为空时返回空字符串。
     */
    public static String extractSourceId(String externalId) {
        if (externalId == null || externalId.isBlank()) return "";
        int idx = externalId.indexOf(':');
        if (idx < 0 || idx == externalId.length() - 1) return "";
        return externalId.substring(idx + 1);
    }
}
