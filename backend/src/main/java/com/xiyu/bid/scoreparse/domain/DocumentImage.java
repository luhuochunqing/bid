package com.xiyu.bid.scoreparse.domain;

/**
 * 投标文件图片证据单元（spec 048 多模态图片评分）。
 * <p>携带章节归属与图像内容，供客观项打分时以 data URL 形式喂给多模态模型。
 * <p>纯核心 record：无框架依赖，可独立单测（Constitution FP-Java）。
 *
 * @param index    图片在清单中的序号（0 起），全局稳定
 * @param mimeType image/png | image/jpeg ...
 * @param section  章节标题（与 extract_headings / BidChapterDirtySet 同口径），PDF 可为空
 * @param pageHint PDF 页码；其它格式为 null
 * @param width    像素宽（供 token 估算）
 * @param height   像素高（供 token 估算）
 * @param base64   data URL 的图像裸 base64（不含 data: 前缀）；由调用方在外壳层组装 data URL
 */
public record DocumentImage(
        int index,
        String mimeType,
        String section,
        Integer pageHint,
        int width,
        int height,
        String base64
) {
}
