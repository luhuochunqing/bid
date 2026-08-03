package com.xiyu.bid.performance.infrastructure;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation;

import java.math.BigInteger;

/**
 * 业绩合订本 Word 页面设置：将 A4 页面尺寸与指定页边距应用到新建的 XWPFDocument。
 *
 * <p>对标 {@code WarehouseWordBundlePageSetup}。
 */
final class PerformanceWordBundlePageSetup {

    private PerformanceWordBundlePageSetup() {
        // 工具类，禁止实例化
    }

    static void applyTo(XWPFDocument doc) {
        CTSectPr sectPr = doc.getDocument().getBody().isSetSectPr()
                ? doc.getDocument().getBody().getSectPr()
                : doc.getDocument().getBody().addNewSectPr();
        CTPageSz pgSz = sectPr.isSetPgSz() ? sectPr.getPgSz() : sectPr.addNewPgSz();
        pgSz.setW(BigInteger.valueOf(PerformanceWordStyleConfig.PAGE_WIDTH_TWIPS));
        pgSz.setH(BigInteger.valueOf(PerformanceWordStyleConfig.PAGE_HEIGHT_TWIPS));
        pgSz.setOrient(STPageOrientation.PORTRAIT);
        CTPageMar pgMar = sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();
        pgMar.setTop(BigInteger.valueOf(PerformanceWordStyleConfig.MARGIN_TOP_TWIPS));
        pgMar.setBottom(BigInteger.valueOf(PerformanceWordStyleConfig.MARGIN_BOTTOM_TWIPS));
        pgMar.setLeft(BigInteger.valueOf(PerformanceWordStyleConfig.MARGIN_LEFT_TWIPS));
        pgMar.setRight(BigInteger.valueOf(PerformanceWordStyleConfig.MARGIN_RIGHT_TWIPS));
    }
}
