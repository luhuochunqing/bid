package com.xiyu.bid.warehouse.infrastructure;

import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.math.BigInteger;

/**
 * Word 合订本页面设置（CO-582 §3.9）。
 * <p>
 * 将 A4 页面尺寸与指定页边距应用到新建的 XWPFDocument。
 */
final class WarehouseWordBundlePageSetup {

    private WarehouseWordBundlePageSetup() {
        // 工具类，禁止实例化
    }

    static void applyTo(XWPFDocument doc) {
        CTSectPr sectPr = doc.getDocument().getBody().isSetSectPr()
                ? doc.getDocument().getBody().getSectPr()
                : doc.getDocument().getBody().addNewSectPr();
        CTPageSz pgSz = sectPr.isSetPgSz() ? sectPr.getPgSz() : sectPr.addNewPgSz();
        pgSz.setW(BigInteger.valueOf(WarehouseWordStyleConfig.PAGE_WIDTH_TWIPS));
        pgSz.setH(BigInteger.valueOf(WarehouseWordStyleConfig.PAGE_HEIGHT_TWIPS));
        pgSz.setOrient(STPageOrientation.PORTRAIT);
        CTPageMar pgMar = sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();
        pgMar.setTop(BigInteger.valueOf(WarehouseWordStyleConfig.MARGIN_TOP_TWIPS));
        pgMar.setBottom(BigInteger.valueOf(WarehouseWordStyleConfig.MARGIN_BOTTOM_TWIPS));
        pgMar.setLeft(BigInteger.valueOf(WarehouseWordStyleConfig.MARGIN_LEFT_TWIPS));
        pgMar.setRight(BigInteger.valueOf(WarehouseWordStyleConfig.MARGIN_RIGHT_TWIPS));
    }
}
