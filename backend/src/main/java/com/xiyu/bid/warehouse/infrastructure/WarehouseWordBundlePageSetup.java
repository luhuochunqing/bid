package com.xiyu.bid.warehouse.infrastructure;

import com.xiyu.bid.common.infrastructure.word.WordBundlePageSetup;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

/**
 * Word 合订本页面设置（CO-582 §3.9）。
 *
 * <p>D3-1 修复：原重复实现已迁移到 {@code com.xiyu.bid.common.infrastructure.word.WordBundlePageSetup}。
 */
final class WarehouseWordBundlePageSetup {

    private WarehouseWordBundlePageSetup() {
        // 工具类，禁止实例化
    }

    static void applyTo(XWPFDocument doc) {
        WordBundlePageSetup.applyTo(doc, WarehouseWordStyleConfig.CONFIG);
    }
}
