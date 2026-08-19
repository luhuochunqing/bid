package com.xiyu.bid.biddraftagent.infrastructure.openai;

/**
 * 多模态图片内容模型（spec 048 transport 层）。
 * <p>打分链路传给 transport 的图片对象，由 transport 外壳组装为
 * OpenAI SDK 的 image_url content part（data URL 形式）。
 *
 * @param mimeType 图片 MIME 类型（image/png | image/jpeg ...）
 * @param base64   图像裸 base64（不含 data: 前缀）
 */
public record MultimodalImage(String mimeType, String base64) {

    /** 组装为 data URL（image_url content part 的标准形态）。 */
    public String dataUrl() {
        return "data:" + mimeType + ";base64," + base64;
    }
}
