package com.xiyu.bid.performance.domain;

import com.xiyu.bid.performance.application.dto.PerformanceDTO;
import com.xiyu.bid.performance.infrastructure.PerformanceAttachmentTypeLabels;

import java.util.List;
import java.util.Set;

/**
 * 附件类型过滤器（纯核心函数）。
 *
 * <p>按 fileType 过滤附件列表，不依赖 Spring、不访问数据，可独立单测。
 * 过滤规则：types 为 null 或空时全部保留（向后兼容），否则仅保留 fileType 在 types 集合内的附件。
 *
 * <p>允许的附件类型白名单引用 {@link PerformanceAttachmentTypeLabels#LABELS} 的 keySet，
 * 避免两处定义不同步导致校验与展示逻辑静默偏差。
 */
public final class AttachmentFilter {

    /** 允许的附件类型白名单（与 PerformanceAttachmentTypeLabels.LABELS 同步）。 */
    public static final Set<String> ALLOWED_TYPES =
            Set.copyOf(PerformanceAttachmentTypeLabels.LABELS.keySet());

    private AttachmentFilter() {}

    /**
     * 按附件类型过滤。
     *
     * @param attachments 原始附件列表
     * @param types 要保留的类型集合；null 或空 = 全部保留（向后兼容）
     * @return 过滤后的列表
     */
    public static List<PerformanceDTO.AttachmentDTO> filterByTypes(
            List<PerformanceDTO.AttachmentDTO> attachments,
            Set<String> types) {
        if (types == null || types.isEmpty()) {
            return attachments;
        }
        return attachments.stream()
                .filter(a -> types.contains(a.fileType()))
                .toList();
    }

    /**
     * 校验附件类型是否合法（防止注入非法 fileType）。
     *
     * @param types 待校验的类型集合；null 直接放行
     * @throws IllegalArgumentException 当存在非法类型时
     */
    public static void validateTypes(Set<String> types) {
        if (types == null) return;
        for (String t : types) {
            if (!ALLOWED_TYPES.contains(t)) {
                throw new IllegalArgumentException("非法附件类型: " + t);
            }
        }
    }
}
