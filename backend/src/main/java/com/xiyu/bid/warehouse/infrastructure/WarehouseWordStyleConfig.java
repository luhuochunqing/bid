package com.xiyu.bid.warehouse.infrastructure;

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
 * | 四级小标题（照片文件名） | 左对齐, 宋体 10.5pt    |
 * | 正文（图片）           | 居中显示               |
 * | 页面尺寸              | A4 (210mm × 297mm)    |
 * | 页边距                | 上下 2.54cm, 左右 2cm  |
 * </pre>
 * 注：页眉页脚暂未实现，后续 PR 补齐。
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
    /**
     * 四级小标题（照片文件名）字号。
     * CO-582 §3.9 要求 10.5pt，POI 中字号以半磅为单位，10.5pt = 21 半磅。
     */
    public static final int SIZE_H4_HALF_PT = 21;

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

    // ========== 图片尺寸计算 ==========

    /** 1 inch = 72 px（POI 默认 EMU/px 换算） */
    public static final int PX_PER_INCH = 72;
    /** 1 inch = 914400 EMU（English Metric Units） */
    public static final int EMU_PER_INCH = 914400;
    /** 正文宽度（twips）= A4 宽 - 左右页边距 */
    public static final int CONTENT_WIDTH_TWIPS = PAGE_WIDTH_TWIPS - MARGIN_LEFT_TWIPS - MARGIN_RIGHT_TWIPS;
}
