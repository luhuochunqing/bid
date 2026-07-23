package com.xiyu.bid.warehouse.infrastructure;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WarehouseWordStyleRegistrar 直接单测。
 *
 * <p>覆盖 CO-582 §3.4 修复根因（POI 默认不生成 styles.xml → 导航窗格为空）的关键契约。
 * 集成测试 {@code WarehouseWordBundleBuilderTest.buildBundle_stylesXmlDefinesHeadingStylesWithOutlineLevel}
 * 只能间接覆盖 outlineLvl/qFormat；字体、字号换算、idempotency、null 防御均需直接单测兜底。</p>
 *
 * <p>被测契约：
 * <ol>
 *   <li>4 个 styleId 全部注册到 styles.xml（Title/Heading1/Heading2/Heading3）</li>
 *   <li>outlineLvl 严格匹配 OOXML 规范（Title=0, H1=0, H2=1, H3=2）—— 导航窗格层级唯一依据</li>
 *   <li>qFormat 全部设置 —— Word 识别为"快速样式"</li>
 *   <li>字体按配置写入 ascii/eastAsia/hAnsi（Title/H1/H2=黑体, H3=宋体）</li>
 *   <li>字号 = pt × 2（OOXML w:sz 单位是半磅，HALF_PT_PER_PT=2）</li>
 *   <li>加粗（标题标准视觉）</li>
 *   <li>幂等性：重复调用不会产生重复样式（POI addStyle 同 ID 覆盖）</li>
 *   <li>null 防御：null doc 必抛 NPE（fail-fast，不让 POI 抛模糊异常）</li>
 * </ol></p>
 */
class WarehouseWordStyleRegistrarTest {

    /**
     * POI 的 w:sz 单位是半磅。
     * 与 {@link WarehouseWordStyleRegistrar} 内部 HALF_PT_PER_PT 保持一致。
     */
    private static final int HALF_PT_PER_PT = 2;

    @Test
    void registerHeadingStyles_registersAllFourStylesById() throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            WarehouseWordStyleRegistrar.registerHeadingStyles(doc);

            XWPFStyles styles = doc.getStyles();
            assertThat(styles)
                    .as("POI 默认不生成 styles.xml，registerHeadingStyles 必须触发 createStyles")
                    .isNotNull();
            assertThat(styles.getStyle("Title")).isNotNull();
            assertThat(styles.getStyle("Heading1")).isNotNull();
            assertThat(styles.getStyle("Heading2")).isNotNull();
            assertThat(styles.getStyle("Heading3")).isNotNull();
        }
    }

    @Test
    void registerHeadingStyles_setsOutlineLvlPerOoxmlSpec() throws Exception {
        // OOXML 规范：Title 与 Heading1 的 outlineLvl 均为 0（顶层），
        // 两者样式 ID 不同 Word 渲染时区分（Title 通常居中且字号更大）。
        try (XWPFDocument doc = new XWPFDocument()) {
            WarehouseWordStyleRegistrar.registerHeadingStyles(doc);

            XWPFStyles styles = doc.getStyles();
            assertOutlineLvl(styles, "Title", 0);
            assertOutlineLvl(styles, "Heading1", 0);
            assertOutlineLvl(styles, "Heading2", 1);
            assertOutlineLvl(styles, "Heading3", 2);
        }
    }

    @Test
    void registerHeadingStyles_setsQFormatOnAllHeadingStyles() throws Exception {
        // qFormat 让 Word 把样式列入"快速样式"——若缺失，样式在 UI 中无法被直接选择。
        try (XWPFDocument doc = new XWPFDocument()) {
            WarehouseWordStyleRegistrar.registerHeadingStyles(doc);

            XWPFStyles styles = doc.getStyles();
            assertQFormat(styles, "Title");
            assertQFormat(styles, "Heading1");
            assertQFormat(styles, "Heading2");
            assertQFormat(styles, "Heading3");
        }
    }

    @Test
    void registerHeadingStyles_appliesConfiguredFontPerStyle() throws Exception {
        // 字体契约（WarehouseWordStyleConfig）：
        //   Title/H1/H2 = 黑体（标题族）
        //   H3         = 宋体（正文族，三级标题在文档结构中更接近正文）
        // 必须同时设置 ascii / eastAsia / hAnsi 三槽，缺一会导致跨平台显示不一致。
        try (XWPFDocument doc = new XWPFDocument()) {
            WarehouseWordStyleRegistrar.registerHeadingStyles(doc);

            XWPFStyles styles = doc.getStyles();
            assertFont(styles, "Title", "黑体");
            assertFont(styles, "Heading1", "黑体");
            assertFont(styles, "Heading2", "黑体");
            assertFont(styles, "Heading3", "宋体");
        }
    }

    @Test
    void registerHeadingStyles_convertsPtToHalfPointForSize() throws Exception {
        // OOXML w:sz 单位是半磅（half-points）：1 pt = 2 half-points
        // 视觉契约：Title=18pt, H1=16pt, H2=14pt, H3=12pt
        try (XWPFDocument doc = new XWPFDocument()) {
            WarehouseWordStyleRegistrar.registerHeadingStyles(doc);

            XWPFStyles styles = doc.getStyles();
            assertSizeHalfPoints(styles, "Title", WarehouseWordStyleConfig.SIZE_TITLE_PT * HALF_PT_PER_PT);
            assertSizeHalfPoints(styles, "Heading1", WarehouseWordStyleConfig.SIZE_H1_PT * HALF_PT_PER_PT);
            assertSizeHalfPoints(styles, "Heading2", WarehouseWordStyleConfig.SIZE_H2_PT * HALF_PT_PER_PT);
            assertSizeHalfPoints(styles, "Heading3", WarehouseWordStyleConfig.SIZE_H3_PT * HALF_PT_PER_PT);
        }
    }

    @Test
    void registerHeadingStyles_isBoldAcrossAllHeadingStyles() throws Exception {
        // 标题族按 UI 规范必须加粗；缺一会让标题与正文视觉层级消失。
        try (XWPFDocument doc = new XWPFDocument()) {
            WarehouseWordStyleRegistrar.registerHeadingStyles(doc);

            XWPFStyles styles = doc.getStyles();
            for (String styleId : new String[]{"Title", "Heading1", "Heading2", "Heading3"}) {
                XWPFStyle style = styles.getStyle(styleId);
                assertThat(style)
                        .as("样式 %s 必须存在", styleId)
                        .isNotNull();
                var rPr = style.getCTStyle().getRPr();
                assertThat(rPr.sizeOfBArray())
                        .as("样式 %s 必须设置 B 加粗（标题族视觉契约）", styleId)
                        .isGreaterThan(0);
            }
        }
    }

    @Test
    void registerHeadingStyles_isIdempotent_repeatedCallDoesNotDuplicateStyles() throws Exception {
        // Javadoc 契约："重复调用安全：若样式 ID 已存在，POI 会覆盖旧定义"
        // 防止任何调用方意外多次调用导致 styles.xml 出现重复定义或抛异常。
        try (XWPFDocument doc = new XWPFDocument()) {
            WarehouseWordStyleRegistrar.registerHeadingStyles(doc);
            WarehouseWordStyleRegistrar.registerHeadingStyles(doc);

            XWPFStyles styles = doc.getStyles();
            // 仍然只有 4 个 styleId（不出现 8 个）
            assertThat(styles.getStyle("Title")).isNotNull();
            assertThat(styles.getStyle("Heading1")).isNotNull();
            assertThat(styles.getStyle("Heading2")).isNotNull();
            assertThat(styles.getStyle("Heading3")).isNotNull();
            // outlineLvl 仍然正确（被覆盖后值未漂移）
            assertOutlineLvl(styles, "Title", 0);
            assertOutlineLvl(styles, "Heading1", 0);
            assertOutlineLvl(styles, "Heading2", 1);
            assertOutlineLvl(styles, "Heading3", 2);
        }
    }

    @Test
    void registerHeadingStyles_throwsNullPointerExceptionOnNullDoc() {
        // fail-fast：不让 POI 抛模糊异常（如 NullPointerException at POI 内部深处）
        assertThatThrownBy(() -> WarehouseWordStyleRegistrar.registerHeadingStyles(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("doc");
    }

    // ========== 断言辅助 ==========

    private static void assertOutlineLvl(XWPFStyles styles, String styleId, int expectedLvl) {
        XWPFStyle style = styles.getStyle(styleId);
        assertThat(style)
                .as("样式 %s 必须存在", styleId)
                .isNotNull();
        CTStyle ctStyle = style.getCTStyle();
        assertThat(ctStyle.isSetPPr())
                .as("样式 %s 必须有 pPr 段落属性", styleId)
                .isTrue();
        assertThat(ctStyle.getPPr().isSetOutlineLvl())
                .as("样式 %s 必须有 outlineLvl（Word 导航窗格识别层级的唯一依据）", styleId)
                .isTrue();
        assertThat(ctStyle.getPPr().getOutlineLvl().getVal().intValue())
                .as("样式 %s 的 outlineLvl 必须为 %d", styleId, expectedLvl)
                .isEqualTo(expectedLvl);
    }

    private static void assertQFormat(XWPFStyles styles, String styleId) {
        XWPFStyle style = styles.getStyle(styleId);
        assertThat(style)
                .as("样式 %s 必须存在", styleId)
                .isNotNull();
        assertThat(style.getCTStyle().isSetQFormat())
                .as("样式 %s 必须有 qFormat 标记（让 Word 识别为快速样式）", styleId)
                .isTrue();
    }

    private static void assertFont(XWPFStyles styles, String styleId, String expectedFont) {
        XWPFStyle style = styles.getStyle(styleId);
        assertThat(style)
                .as("样式 %s 必须存在", styleId)
                .isNotNull();
        var rPr = style.getCTStyle().getRPr();
        assertThat(rPr.sizeOfRFontsArray())
                .as("样式 %s 必须设置 rFonts（中文标题需要 ascii/eastAsia/hAnsi 三槽）", styleId)
                .isGreaterThan(0);
        var rFonts = rPr.getRFontsArray(0);
        assertThat(rFonts.getAscii())
                .as("样式 %s ascii 字体", styleId)
                .isEqualTo(expectedFont);
        assertThat(rFonts.getEastAsia())
                .as("样式 %s eastAsia 字体", styleId)
                .isEqualTo(expectedFont);
        assertThat(rFonts.getHAnsi())
                .as("样式 %s hAnsi 字体", styleId)
                .isEqualTo(expectedFont);
    }

    private static void assertSizeHalfPoints(XWPFStyles styles, String styleId, int expectedHalfPoints) {
        XWPFStyle style = styles.getStyle(styleId);
        assertThat(style)
                .as("样式 %s 必须存在", styleId)
                .isNotNull();
        var rPr = style.getCTStyle().getRPr();
        assertThat(rPr.sizeOfSzArray())
                .as("样式 %s 必须设置字号 sz", styleId)
                .isGreaterThan(0);
        assertThat(Integer.parseInt(rPr.getSzArray(0).xgetVal().getStringValue()))
                .as("样式 %s 字号（半磅）", styleId)
                .isEqualTo(expectedHalfPoints);
    }
}
