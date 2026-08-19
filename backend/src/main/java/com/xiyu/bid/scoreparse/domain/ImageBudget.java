package com.xiyu.bid.scoreparse.domain;

/**
 * 单个评分项的图片配给预算（spec 048 多模态图片评分）。
 * <p>数值来自 application 配置（app.score-parse.multimodal.*），注入纯策略；
 * 预算本身为纯核心输入，非 IO。
 *
 * @param maxImagesPerItem    单评分项最多携带图片数
 * @param maxImageBytesPerItem 单评分项图片累计字节上限（base64 后）
 */
public record ImageBudget(
        int maxImagesPerItem,
        long maxImageBytesPerItem
) {
}
