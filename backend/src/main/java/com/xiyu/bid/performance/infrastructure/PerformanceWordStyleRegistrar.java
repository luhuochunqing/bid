package com.xiyu.bid.performance.infrastructure;

import com.xiyu.bid.common.infrastructure.word.WordStyleRegistrar;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

/**
 * 业绩合订本 Word 标题样式注册器（委托给公共 {@link WordStyleRegistrar}）。
 *
 * <p>D3-1 修复：原重复实现已迁移到 {@code com.xiyu.bid.common.infrastructure.word.WordStyleRegistrar}，
 * 本类保留为门面，避免 Performance 模块内部调用方改动过大。
 */
public final class PerformanceWordStyleRegistrar {

    private PerformanceWordStyleRegistrar() {
        // 工具类，禁止实例化
    }

    /**
     * 向文档注册 Title/Heading1-4 五个标题样式定义。
     *
     * <p>调用时机：在 {@link PerformanceWordBundlePageSetup#applyTo} 之后、生成任何段落之前。
     * 重复调用安全：若样式 ID 已存在，POI 会覆盖旧定义。
     */
    public static void registerHeadingStyles(XWPFDocument doc) {
        WordStyleRegistrar.registerHeadingStyles(doc, PerformanceWordStyleConfig.CONFIG);
    }
}
