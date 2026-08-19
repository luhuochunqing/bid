// Input: sidecar /convert-images 响应 JSON
// Output: 抽图结果值对象（防御式过滤后的图片清单 + 截断信号）
// Pos: scoreparse/application — spec 048 T012，契约 contracts/sidecar-images-api.md
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.DocumentImage;

import java.util.List;

/**
 * 投标文件图片抽取结果（data-model.md §SidecarImageExtraction）。
 * <p>truncated=true 表示 sidecar 侧总图字节预算截断（仅可观测信号，不影响已返回图片可用性）。
 */
public record SidecarImageExtraction(List<DocumentImage> images, boolean truncated) {

    public SidecarImageExtraction {
        images = images == null ? List.of() : List.copyOf(images);
    }

    public static SidecarImageExtraction empty() {
        return new SidecarImageExtraction(List.of(), false);
    }
}
