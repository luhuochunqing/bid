package com.xiyu.bid.warehouse.infrastructure;

import com.xiyu.bid.common.infrastructure.word.WordStyleConfig;

import java.util.List;

/**
 * 仓库 Word 合订本文档样式常量（CO-582 §3.9）。
 * <p>
 * 纯常量定义，无业务逻辑。
 * <p>
 * 样式规范：
 * <pre>
 * | 元素                  | 样式                  |
 * |----------------------|----------------------|
 * | 文档标题              | 居中, 黑体 18pt, 加粗  |
 * | 一级标题（省份）       | 左对齐, 黑体 16pt, 加粗 |
 * | 二级标题（仓库名）     | 左对齐, 黑体 14pt, 加粗 |
 * | 三级标题（附件分类）    | 左对齐, 宋体 12pt, 加粗 |
 * | 正文（图片）           | 居中显示               |
 * | 页面尺寸              | A4 (210mm × 297mm)    |
 * | 页边距                | 上下 2.54cm, 左右 2cm  |
 * </pre>
 * 注：页眉页脚暂未实现，后续 PR 补齐。
 *
 * <p>D3-1 修复：常量保留为向后兼容，新增 {@link #CONFIG} 实例供 common 组件使用。
 */
public final class WarehouseWordStyleConfig {

    private WarehouseWordStyleConfig() {
        // 常量类，禁止实例化
    }

    // ========== 字体 ==========

    /** 黑体（标题专用） */
    public static final String FONT_HEITI = "黑体";
    /** 宋体（正文/三级标题/小标题） */
    public static final String FONT_SONGTI = "宋体";

    // ========== 字号（单位：pt） ==========

    /** 文档标题字号 */
    public static final int SIZE_TITLE_PT = 18;
    /** 一级标题（省份）字号 */
    public static final int SIZE_H1_PT = 16;
    /** 二级标题（仓库名）字号 */
    public static final int SIZE_H2_PT = 14;
    /** 三级标题（附件分类）字号 */
    public static final int SIZE_H3_PT = 12;

    // ========== 页面尺寸（单位：twips，1 inch = 1440 twips） ==========

    /** A4 宽度（210mm = 11906 twips） */
    public static final int PAGE_WIDTH_TWIPS = 11906;
    /** A4 高度（297mm = 16838 twips） */
    public static final int PAGE_HEIGHT_TWIPS = 16838;
    /** 上下页边距（2.54cm = 1 inch = 1440 twips） */
    public static final int MARGIN_TOP_TWIPS = 1440;
    public static final int MARGIN_BOTTOM_TWIPS = 1440;
    /** 左右页边距（2cm ≈ 1134 twips） */
    public static final int MARGIN_LEFT_TWIPS = 1134;
    public static final int MARGIN_RIGHT_TWIPS = 1134;

    // ========== PDF 渲染 ==========

    /**
     * PDF 渲染 DPI（CO-582：96 DPI，平衡清晰度与内存）。
     * <p>
     * PDFRenderer.renderImageWithDPI 使用此值。72 DPI 模糊，150 DPI 内存消耗大。
     */
    public static final int PDF_RENDER_DPI = 96;

    /**
     * 单文件最大 PDF 页数（0 表示不限制，仓库模块默认全量渲染）。
     */
    public static final int MAX_PDF_PAGES_PER_FILE = 0;

    /**
     * JPEG 压缩质量（0.0-1.0）。仓库模块当前未启用 JPEG 压缩，保留默认值供 {@link WordStyleConfig} 使用。
     */
    public static final float JPEG_COMPRESSION_QUALITY = 0.85f;

    // ========== 图片尺寸计算 ==========

    /** 1 inch = 72 px（POI 默认 EMU/px 换算） */
    public static final int PX_PER_INCH = 72;
    /** 1 inch = 914400 EMU（English Metric Units） */
    public static final int EMU_PER_INCH = 914400;
    /** 正文宽度（twips）= A4 宽 - 左右页边距 */
    public static final int CONTENT_WIDTH_TWIPS = PAGE_WIDTH_TWIPS - MARGIN_LEFT_TWIPS - MARGIN_RIGHT_TWIPS;

    // ========== D3-1 修复：统一配置实例 ==========

    /**
     * 仓库合订本样式配置实例，供 {@link com.xiyu.bid.common.infrastructure.word.WordStyleRegistrar}
     * 和 {@link com.xiyu.bid.common.infrastructure.word.WordBundlePageSetup} 使用。
     */
    public static final WordStyleConfig CONFIG = new WordStyleConfig(
            FONT_HEITI, SIZE_TITLE_PT,
            List.of(
                    new WordStyleConfig.HeadingSpec(FONT_HEITI, SIZE_H1_PT, true),
                    new WordStyleConfig.HeadingSpec(FONT_HEITI, SIZE_H2_PT, true),
                    new WordStyleConfig.HeadingSpec(FONT_SONGTI, SIZE_H3_PT, true)
            ),
            PDF_RENDER_DPI, MAX_PDF_PAGES_PER_FILE, JPEG_COMPRESSION_QUALITY,
            PAGE_WIDTH_TWIPS, PAGE_HEIGHT_TWIPS,
            MARGIN_TOP_TWIPS, MARGIN_BOTTOM_TWIPS,
            MARGIN_LEFT_TWIPS, MARGIN_RIGHT_TWIPS
    );
}
