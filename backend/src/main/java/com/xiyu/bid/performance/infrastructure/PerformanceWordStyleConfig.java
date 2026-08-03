package com.xiyu.bid.performance.infrastructure;

/**
 * 业绩合订本 Word 文档样式常量。
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
 * <p>高清渲染：PDF 转 PNG 使用 300 DPI，保证附件源文件清晰度不被压缩。
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

    /** 文档标题字号 */
    public static final int SIZE_TITLE_PT = 20;
    /** H1 客户类型字号 */
    public static final int SIZE_H1_PT = 18;
    /** H2 集团名称字号 */
    public static final int SIZE_H2_PT = 16;
    /** H3 合同名称 + 附件分类标签字号 */
    public static final int SIZE_H3_PT = 14;
    /** H4 中标通知书字号 */
    public static final int SIZE_H4_PT = 12;

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

    // ========== 高清 PDF 渲染（关键：不允许压缩源文件清晰度） ==========

    /**
     * PDF 渲染 DPI — 300 DPI 高清模式。
     * <p>需求 §2：生成合订本时不允许压缩源文件清晰度，必须保证高清。
     * <ul>
     *   <li>72 DPI：模糊，不可接受</li>
     *   <li>96 DPI：仓库模块使用，平衡清晰度与内存</li>
     *   <li>150 DPI：内存消耗大但仍不够清晰</li>
     *   <li>300 DPI：印刷级高清，业绩合订本采用此值</li>
     * </ul>
     * 内存管理：单页渲染后立即写入 Word 并释放 BufferedImage，
     * 避免大文档 OOM。
     */
    public static final int PDF_RENDER_DPI = 300;

    /**
     * 单个 PDF 最大渲染页数 — 防止超大 PDF（如 100+ 页合同）撑爆堆内存。
     * <p>300 DPI 下单页 BufferedImage ~35MB + JPEG 编码 ~500KB，
     * 30 页约 1.05GB BufferedImage（逐页释放）+ 15MB JPEG 字节累积。
     */
    public static final int MAX_PDF_PAGES_PER_FILE = 30;

    /**
     * JPEG 压缩质量（0.0-1.0）。
     * <p>300 DPI 保证高清分辨率，JPEG 0.85 保证视觉质量，
     * 同时内存占用仅为 PNG 的 1/5~1/10，避免大批量导出 OOM。
     */
    public static final float JPEG_COMPRESSION_QUALITY = 0.85f;

    // ========== 图片尺寸计算 ==========

    /** 1 inch = 72 px（POI 默认 EMU/px 换算） */
    public static final int PX_PER_INCH = 72;
    /** 1 inch = 914400 EMU（English Metric Units） */
    public static final int EMU_PER_INCH = 914400;
    /** 正文宽度（twips）= A4 宽 - 左右页边距 */
    public static final int CONTENT_WIDTH_TWIPS = PAGE_WIDTH_TWIPS - MARGIN_LEFT_TWIPS - MARGIN_RIGHT_TWIPS;
}
