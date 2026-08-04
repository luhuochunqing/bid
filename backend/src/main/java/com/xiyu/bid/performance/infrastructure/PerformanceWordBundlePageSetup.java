package com.xiyu.bid.performance.infrastructure;

import com.xiyu.bid.common.infrastructure.word.WordBundlePageSetup;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

/**
 * 业绩合订本 Word 页面设置（委托给公共 {@link WordBundlePageSetup}）。
 *
 * <p>D3-1 修复：原重复实现已迁移到 {@code com.xiyu.bid.common.infrastructure.word.WordBundlePageSetup}。
 */
final class PerformanceWordBundlePageSetup {

    private PerformanceWordBundlePageSetup() {
        // 工具类，禁止实例化
    }

    static void applyTo(XWPFDocument doc) {
        WordBundlePageSetup.applyTo(doc, PerformanceWordStyleConfig.CONFIG);
    }
}
