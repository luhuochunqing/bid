package com.xiyu.bid.warehouse.infrastructure;

import com.xiyu.bid.common.infrastructure.word.WordStyleRegistrar;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

/**
 * 向 XWPFDocument 注册 Word 内置标题样式（Title/Heading1-3）到 word/styles.xml。
 *
 * <p>根因（CO-582 §3.4 彻底修复）：
 * Apache POI 的 {@code new XWPFDocument()} 默认不生成 {@code word/styles.xml}。
 * 即使段落调用 {@code p.setStyle("Heading1")}，也只是把 {@code <w:pStyle w:val="Heading1"/>}
 * 写到 {@code document.xml}，而 styles.xml 中没有对应样式定义——
 * Word/WPS 打开后无法识别为标题，导航窗格为空。
 *
 * <p>D3-1 修复：原重复实现已迁移到 {@code com.xiyu.bid.common.infrastructure.word.WordStyleRegistrar}，
 * 本类保留为门面，避免 Warehouse 模块内部调用方改动过大。
 */
public final class WarehouseWordStyleRegistrar {

    private WarehouseWordStyleRegistrar() {
        // 工具类，禁止实例化
    }

    /**
     * 向文档注册 Title/Heading1-3 四个标题样式定义。
     *
     * <p>调用时机：在 {@link WarehouseWordBundlePageSetup#applyTo} 之后、生成任何段落之前。
     * 重复调用安全：若样式 ID 已存在，POI 会覆盖旧定义。
     */
    public static void registerHeadingStyles(XWPFDocument doc) {
        WordStyleRegistrar.registerHeadingStyles(doc, WarehouseWordStyleConfig.CONFIG);
    }
}
