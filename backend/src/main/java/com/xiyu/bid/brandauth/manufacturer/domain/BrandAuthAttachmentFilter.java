package com.xiyu.bid.brandauth.manufacturer.domain;

import com.xiyu.bid.brandauth.manufacturer.domain.valueobject.AttachmentType;

import java.util.List;
import java.util.Set;

/**
 * 附件类型过滤器（纯核心函数）.
 *
 * <p>按 attachmentType 过滤附件，不依赖 Spring、不访问数据，可独立单测。
 * 过滤规则：types 为 null 或空时全部保留（向后兼容），否则仅保留
 * attachmentType.name() 在 types 集合内的附件。
 */
public final class BrandAuthAttachmentFilter {

    /** 允许的附件类型白名单（4 种 AttachmentType）。 */
    public static final Set<String> ALLOWED_TYPES = Set.of(
            "AUTH_DOC", "SUPPLEMENTARY", "AGENT_AUTH_1", "AGENT_AUTH_2"
    );

    private BrandAuthAttachmentFilter() {}

    /**
     * 判断单个附件类型是否匹配选中类型集合.
     *
     * @param type 附件的 AttachmentType
     * @param selectedTypes 用户选中的类型名称集合；null 或空 = 全部匹配
     * @return true 表示保留
     */
    public static boolean matches(final AttachmentType type,
                                   final List<String> selectedTypes) {
        if (selectedTypes == null || selectedTypes.isEmpty()) {
            return true;
        }
        return selectedTypes.contains(type.name());
    }

    /**
     * 校验附件类型是否合法（防止注入非法 attachmentType）.
     *
     * @param types 待校验的类型集合；null 直接放行
     * @throws IllegalArgumentException 当存在非法类型时
     */
    public static void validateTypes(final List<String> types) {
        if (types == null) return;
        for (String t : types) {
            if (!ALLOWED_TYPES.contains(t)) {
                throw new IllegalArgumentException("非法附件类型: " + t);
            }
        }
    }
}
