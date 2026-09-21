package com.xiyu.bid.compliance.domain;

/** 质检链路的页面图像值对象，图片仅在本次任务内存中流转。 */
public record PageImage(
        int index,
        String mimeType,
        String section,
        Integer pageHint,
        int width,
        int height,
        String base64
) {
}
