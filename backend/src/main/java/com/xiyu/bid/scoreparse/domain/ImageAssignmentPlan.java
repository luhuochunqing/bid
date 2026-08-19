package com.xiyu.bid.scoreparse.domain;

import java.util.List;

/**
 * 某个评分项实际携带的图片子集 + 裁剪/降级信号（spec 048，research R4）。
 * <p>不变量（data-model §5）：{@code degraded=true ⇒ images 为空}——降级即纯文本，
 * 不允许同时携带图片；违反时构造期抛出（纯核心断言，单测覆盖）。
 *
 * @param images    该评分项配给的图片（已裁剪），可能为空
 * @param truncated 超预算发生确定性裁剪时 true
 * @param degraded  走纯文本（抽图失败/损坏/模型不支持）时 true
 */
public record ImageAssignmentPlan(
        List<DocumentImage> images,
        boolean truncated,
        boolean degraded
) {

    public ImageAssignmentPlan {
        images = images == null ? List.of() : List.copyOf(images);
        if (degraded && !images.isEmpty()) {
            throw new IllegalArgumentException(
                    "degraded plan must not carry images: degraded=true requires empty images");
        }
    }

    /** 纯文本降级：images=[] truncated=false degraded=true。 */
    public static ImageAssignmentPlan none() {
        return new ImageAssignmentPlan(List.of(), false, true);
    }

    public boolean hasImages() {
        return !images.isEmpty();
    }
}
