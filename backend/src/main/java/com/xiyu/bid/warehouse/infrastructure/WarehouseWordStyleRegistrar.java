package com.xiyu.bid.warehouse.infrastructure;

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
 * 向 XWPFDocument 注册 Word 内置标题样式（Title/Heading1-3）到 word/styles.xml。
 * <p>
 * 根因（CO-582 §3.4 彻底修复）：
 * Apache POI 的 {@code new XWPFDocument()} 默认不生成 {@code word/styles.xml}。
 * 即使段落调用 {@code p.setStyle("Heading1")}，也只是把 {@code <w:pStyle w:val="Heading1"/>}
 * 写到 {@code document.xml}，而 styles.xml 中没有对应样式定义——
 * Word/WPS 打开后无法识别为标题，导航窗格为空。
 * <p>
 * 修复：在文档创建后立即注册 Title/Heading1/Heading2/Heading3 四个样式定义，
 * 每个样式必须包含：
 * <ul>
 *   <li>{@code w:qFormat} —— 让 Word 把它列入"快速样式"</li>
 *   <li>{@code w:pPr/w:outlineLvl w:val="N"} —— 大纲级别（Word 导航窗格识别层级的唯一依据）</li>
 *   <li>{@code w:rFonts/w:sz/w:b} —— 字体/字号/加粗</li>
 * </ul>
 * <p>
 * 受 FP-Java 架构保护：本类只依赖 POI 类型，不依赖 Controller/Repository/Config。
 */
public final class WarehouseWordStyleRegistrar {

    /**
     * 大纲级别常量（OOXML w:outlineLvl w:val 取值）。
     * <p>
     * OOXML 规范规定 Title 与 Heading1 的 outlineLvl 均为 0（顶层）。
     * 两者样式 ID 不同，Word 渲染时会区分（Title 通常居中且字号更大）。
     */
    private static final int OUTLINE_LEVEL_TOP = 0;
    private static final int OUTLINE_LEVEL_H2 = 1;
    private static final int OUTLINE_LEVEL_H3 = 2;

    /**
     * 字号换算：1 pt = 2 半磅（OOXML w:sz 单位是半磅）。
     */
    private static final int HALF_PT_PER_PT = 2;

    private WarehouseWordStyleRegistrar() {
        // 工具类，禁止实例化
    }

    /**
     * 向文档注册 Title/Heading1/Heading2/Heading3 四个标题样式定义。
     * <p>
     * 调用时机：在 {@link WarehouseWordBundlePageSetup#applyTo} 之后、生成任何段落之前。
     * 重复调用安全：若样式 ID 已存在，POI 会覆盖旧定义。
     *
     * @param doc 目标文档，不能为 null
     */
    public static void registerHeadingStyles(XWPFDocument doc) {
        if (doc == null) {
            throw new NullPointerException("doc");
        }
        XWPFStyles styles = doc.createStyles();

        registerStyle(styles, "Title", "Title",
                WarehouseWordStyleConfig.FONT_HEITI,
                WarehouseWordStyleConfig.SIZE_TITLE_PT,
                true, OUTLINE_LEVEL_TOP);
        registerStyle(styles, "Heading1", "heading 1",
                WarehouseWordStyleConfig.FONT_HEITI,
                WarehouseWordStyleConfig.SIZE_H1_PT,
                true, OUTLINE_LEVEL_TOP);
        registerStyle(styles, "Heading2", "heading 2",
                WarehouseWordStyleConfig.FONT_HEITI,
                WarehouseWordStyleConfig.SIZE_H2_PT,
                true, OUTLINE_LEVEL_H2);
        registerStyle(styles, "Heading3", "heading 3",
                WarehouseWordStyleConfig.FONT_SONGTI,
                WarehouseWordStyleConfig.SIZE_H3_PT,
                true, OUTLINE_LEVEL_H3);
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
