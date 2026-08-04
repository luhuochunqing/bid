package com.xiyu.bid.common.infrastructure.word;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation;

import java.math.BigInteger;

/**
 * Word 合订本页面设置（公共组件，CO-602 设计评估 D3-1 修复）。
 *
 * <p>将 A4 页面尺寸与指定页边距应用到新建的 XWPFDocument。
 * 通过 {@link WordStyleConfig} 接收不同模块的页面参数。
 */
public final class WordBundlePageSetup {

    private WordBundlePageSetup() {
        // 工具类，禁止实例化
    }

    /**
     * 将 {@link WordStyleConfig} 中的页面尺寸/页边距应用到文档。
     */
    public static void applyTo(XWPFDocument doc, WordStyleConfig config) {
        CTSectPr sectPr = doc.getDocument().getBody().isSetSectPr()
                ? doc.getDocument().getBody().getSectPr()
                : doc.getDocument().getBody().addNewSectPr();
        CTPageSz pgSz = sectPr.isSetPgSz() ? sectPr.getPgSz() : sectPr.addNewPgSz();
        pgSz.setW(BigInteger.valueOf(config.pageWidthTwips()));
        pgSz.setH(BigInteger.valueOf(config.pageHeightTwips()));
        pgSz.setOrient(STPageOrientation.PORTRAIT);
        CTPageMar pgMar = sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();
        pgMar.setTop(BigInteger.valueOf(config.marginTopTwips()));
        pgMar.setBottom(BigInteger.valueOf(config.marginBottomTwips()));
        pgMar.setLeft(BigInteger.valueOf(config.marginLeftTwips()));
        pgMar.setRight(BigInteger.valueOf(config.marginRightTwips()));
    }
}
