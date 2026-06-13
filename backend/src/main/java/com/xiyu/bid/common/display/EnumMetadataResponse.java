package com.xiyu.bid.common.display;

import java.util.List;

/**
 * 枚举元数据响应体。
 * 供前端统一拉取所有 DisplayableEnum 的值-标签映射。
 *
 * @param enumName 枚举类名（如 {@code QualificationMatchStatus}）
 * @param values   该枚举的所有值-标签对
 */
public record EnumMetadataResponse(
        String enumName,
        List<EnumPair> values
) {
    public record EnumPair(String name, String displayName) {}
}
