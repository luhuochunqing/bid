package com.xiyu.bid.common.infrastructure.word;

import java.util.List;

/**
 * Word 合订本文档样式配置（CO-602 设计评估 D3-1 修复）。
 *
 * <p>不同模块（仓库/业绩）通过此 record 提供各自的样式参数，
 * 供 {@link WordStyleRegistrar} 统一注册到 XWPFDocument。
 *
 * <p>常量维度：
 * <ul>
 *   <li>页面尺寸/页边距（twips，1 inch = 1440 twips）</li>
 *   <li>PDF 渲染 DPI / 最大页数 / JPEG 压缩质量</li>
 *   <li>标题字体/字号 + 各级 Heading 配置</li>
 *   <li>EMU/px 换算常量</li>
 * </ul>
 */
public record WordStyleConfig(
        String titleFont,
        int titleSizePt,
        List<HeadingSpec> headings,
        int pdfRenderDpi,
        int maxPdfPagesPerFile,
        float jpegCompressionQuality,
        int pageWidthTwips,
        int pageHeightTwips,
        int marginTopTwips,
        int marginBottomTwips,
        int marginLeftTwips,
        int marginRightTwips
) {

    /** 1 inch = 72 px（POI 默认 EMU/px 换算） */
    public static final int PX_PER_INCH = 72;
    /** 1 inch = 914400 EMU（English Metric Units） */
    public static final int EMU_PER_INCH = 914400;

    /** 正文宽度（twips）= A4 宽 - 左右页边距 */
    public int contentWidthTwips() {
        return pageWidthTwips - marginLeftTwips - marginRightTwips;
    }

    /**
     * 单级标题配置。
     *
     * @param font 字体（如"黑体"/"宋体"）
     * @param sizePt 字号（磅）
     * @param bold 是否加粗
     */
    public record HeadingSpec(String font, int sizePt, boolean bold) {}
}
