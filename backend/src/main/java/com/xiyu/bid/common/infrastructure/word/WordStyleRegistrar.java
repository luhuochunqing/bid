package com.xiyu.bid.common.infrastructure.word;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPrGeneral;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STStyleType;

import java.math.BigInteger;

/**
 * Word 标题样式注册器（公共组件，CO-602 设计评估 D3-1 修复）。
 *
 * <p>根因说明（参考 CO-582 §3.4 彻底修复）：
 * Apache POI 的 {@code new XWPFDocument()} 默认不生成 {@code word/styles.xml}。
 * 即使段落调用 {@code p.setStyle("Heading1")}，也只是把 {@code <w:pStyle w:val="Heading1"/>}
 * 写到 {@code document.xml}，而 styles.xml 中没有对应样式定义——
 * Word/WPS 打开后无法识别为标题，导航窗格为空。
 *
 * <p>本类统一了 {@code WarehouseWordStyleRegistrar} 和 {@code PerformanceWordStyleRegistrar}
 * 中逐字重复的 {@code registerStyle} 方法，通过 {@link WordStyleConfig} 接收不同模块的样式配置。
 *
 * <p>每个样式包含：
 * <ul>
 *   <li>{@code w:qFormat} —— 让 Word 把它列入"快速样式"</li>
 *   <li>{@code w:pPr/w:outlineLvl w:val="N"} —— 大纲级别（Word 导航窗格识别层级的唯一依据）</li>
 *   <li>{@code w:rFonts/w:sz/w:b} —— 字体/字号/加粗</li>
 * </ul>
 */
public final class WordStyleRegistrar {

    /** 字号换算：1 pt = 2 半磅（OOXML w:sz 单位是半磅）。 */
    private static final int HALF_PT_PER_PT = 2;

    private WordStyleRegistrar() {
        // 工具类，禁止实例化
    }

    /**
     * 向文档注册 Title + Heading1..N 标题样式定义。
     *
     * <p>调用时机：在 {@link WordBundlePageSetup#applyTo} 之后、生成任何段落之前。
     * 重复调用安全：若样式 ID 已存在，POI 会覆盖旧定义。
     *
     * @param doc 目标文档，不能为 null
     * @param config 样式配置（字体/字号/标题层级数）
     */
    public static void registerHeadingStyles(XWPFDocument doc, WordStyleConfig config) {
        if (doc == null) {
            throw new NullPointerException("doc");
        }
        XWPFStyles styles = doc.createStyles();

        // Title 样式（outlineLvl=0，居中）
        registerStyle(styles, "Title", "Title",
                config.titleFont(), config.titleSizePt(),
                true, 0);

        // Heading1..N 样式（outlineLvl=0..N-1）
        var headings = config.headings();
        for (int i = 0; i < headings.size(); i++) {
            WordStyleConfig.HeadingSpec h = headings.get(i);
            int level = i + 1;
            registerStyle(styles, "Heading" + level, "heading " + level,
                    h.font(), h.sizePt(), h.bold(),
                    Math.min(i, Integer.MAX_VALUE));  // outlineLvl: H1=0, H2=1, ...
        }
    }

    private static void registerStyle(XWPFStyles styles, String styleId, String styleName,
                                       String font, int fontSizePt, boolean bold, int outlineLvl) {
        CTStyle ctStyle = CTStyle.Factory.newInstance();
        ctStyle.setStyleId(styleId);
        ctStyle.setType(STStyleType.PARAGRAPH);
        ctStyle.addNewName().setVal(styleName);
        ctStyle.addNewBasedOn().setVal("Normal");
        ctStyle.addNewNext().setVal("Normal");
        // qFormat：让 Word 把它识别为"快速样式"（标题）
        ctStyle.addNewQFormat();
        // outlineLvl：Word 导航窗格识别层级的唯一依据
        CTPPrGeneral ppr = ctStyle.addNewPPr();
        ppr.addNewOutlineLvl().setVal(BigInteger.valueOf(outlineLvl));
        // 字符属性
        CTRPr rpr = ctStyle.addNewRPr();
        CTFonts fonts = rpr.addNewRFonts();
        fonts.setAscii(font);
        fonts.setEastAsia(font);
        fonts.setHAnsi(font);
        rpr.addNewSz().setVal(BigInteger.valueOf(fontSizePt * HALF_PT_PER_PT));
        if (bold) {
            rpr.addNewB();
        }

        XWPFStyle xwpfStyle = new XWPFStyle(ctStyle);
        styles.addStyle(xwpfStyle);
    }
}
