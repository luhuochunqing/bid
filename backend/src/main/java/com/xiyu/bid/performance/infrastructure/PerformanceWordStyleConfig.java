package com.xiyu.bid.performance.infrastructure;

import com.xiyu.bid.common.infrastructure.word.WordStyleConfig;

import java.util.List;

/**
 * 业绩合订本 Word 文档样式配置（CO-602 设计评估 D3-1 修复）。
 *
 * <p>对标 {@code WarehouseWordStyleConfig}，扩展为四级标题结构：
 * <pre>
 * | 元素                       | 样式                  |
 * |---------------------------|----------------------|
 * | 文档标题                    | 居中, 黑体 20pt, 加粗  |
 * | H1 客户类型（央企/国企/民企）  | 左对齐, 黑体 18pt, 加粗 |
 * | H2 集团名称                  | 左对齐, 黑体 16pt, 加粗 |
 * | H3 合同名称 + 附件分类标签    | 左对齐, 黑体 14pt, 加粗 |
 * | H4 中标通知书                | 左对齐, 宋体 12pt, 加粗 |
 * | 正文（图片）                 | 居中显示               |
 * | 页面尺寸                    | A4 (210mm × 297mm)    |
 * | 页边距                     | 上下 2.54cm, 左右 2cm  |
 * </pre>
 *
 * <p>PDF 渲染：PDF 转 PNG 使用 150 DPI（OOM 修复，原 300 DPI 导致 30 条业绩导出 OOM）。
 *
 * <p>D3-1 修复：常量保留为向后兼容，新增 {@link #CONFIG} 实例供 common 组件使用。
 */
public final class PerformanceWordStyleConfig {

    private PerformanceWordStyleConfig() {
        // 常量类，禁止实例化
    }

    // ========== 字体 ==========

    /** 黑体（标题专用） */
    public static final String FONT_HEITI = "黑体";
    /** 宋体（H4/正文） */
    public static final String FONT_SONGTI = "宋体";

    // ========== 字号（单位：pt） ==========

    public static final int SIZE_TITLE_PT = 20;
    public static final int SIZE_H1_PT = 18;
    public static final int SIZE_H2_PT = 16;
    public static final int SIZE_H3_PT = 14;
    public static final int SIZE_H4_PT = 12;

    // ========== 页面尺寸（单位：twips） ==========

    public static final int PAGE_WIDTH_TWIPS = 11906;
    public static final int PAGE_HEIGHT_TWIPS = 16838;
    public static final int MARGIN_TOP_TWIPS = 1440;
    public static final int MARGIN_BOTTOM_TWIPS = 1440;
    public static final int MARGIN_LEFT_TWIPS = 1134;
    public static final int MARGIN_RIGHT_TWIPS = 1134;

    // ========== PDF 渲染（OOM 修复：降 DPI + 降页数上限） ==========

    /**
     * PDF 渲染 DPI。原 300 DPI 会导致 30 条业绩导出 OOM（A4@300DPI=26MB/页 × 900 页 = 23.5GB）。
     * 降至 150 DPI（A4@150DPI≈6.5MB/页），视觉质量可接受，内存占用降至 1/4。
     */
    public static final int PDF_RENDER_DPI = 150;
    /** 单个 PDF 最多渲染页数。原 30 页过多，降至 10 页控制单文件内存占用。 */
    public static final int MAX_PDF_PAGES_PER_FILE = 10;
    public static final float JPEG_COMPRESSION_QUALITY = 0.85f;

    // ========== 图片尺寸计算 ==========

    public static final int PX_PER_INCH = 72;
    public static final int EMU_PER_INCH = 914400;
    public static final int CONTENT_WIDTH_TWIPS = PAGE_WIDTH_TWIPS - MARGIN_LEFT_TWIPS - MARGIN_RIGHT_TWIPS;

    // ========== D3-1 修复：统一配置实例 ==========

    /**
     * 业绩合订本样式配置实例，供 {@link com.xiyu.bid.common.infrastructure.word.WordStyleRegistrar}
     * 和 {@link com.xiyu.bid.common.infrastructure.word.WordBundlePageSetup} 使用。
     */
    public static final WordStyleConfig CONFIG = new WordStyleConfig(
            FONT_HEITI, SIZE_TITLE_PT,
            List.of(
                    new WordStyleConfig.HeadingSpec(FONT_HEITI, SIZE_H1_PT, true),
                    new WordStyleConfig.HeadingSpec(FONT_HEITI, SIZE_H2_PT, true),
                    new WordStyleConfig.HeadingSpec(FONT_HEITI, SIZE_H3_PT, true),
                    new WordStyleConfig.HeadingSpec(FONT_SONGTI, SIZE_H4_PT, true)
            ),
            PDF_RENDER_DPI, MAX_PDF_PAGES_PER_FILE, JPEG_COMPRESSION_QUALITY,
            PAGE_WIDTH_TWIPS, PAGE_HEIGHT_TWIPS,
            MARGIN_TOP_TWIPS, MARGIN_BOTTOM_TWIPS,
            MARGIN_LEFT_TWIPS, MARGIN_RIGHT_TWIPS
    );
}
