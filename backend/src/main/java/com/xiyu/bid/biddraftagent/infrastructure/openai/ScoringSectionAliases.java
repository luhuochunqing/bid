// Input: none (constant holder)
// Output: canonical list of scoring section alias labels used in Chinese tender documents
// Pos: biddraftagent/infrastructure/openai - single source of truth for scoring section aliases
package com.xiyu.bid.biddraftagent.infrastructure.openai;

import java.util.List;

/**
 * 评分标准章节标题别名词表。
 *
 * <p>招标文件中评分标准所在章节的标题有多种叫法，不同招标主体、不同行业
 * 使用的术语各异。本类是 Prompt 注入和 {@link ScoringSectionLocator} 章节定位的
 * <strong>唯一真相来源</strong>，避免多处列表不同步。
 *
 * <p>对标 {@link PurchaserAliases} 的设计模式：
 * <ul>
 *   <li>ALL - 明确标签列表，Prompt 和 Locator 共用</li>
 *   <li>DISPLAY - 用于注入 Prompt 的展示文案</li>
 * </ul>
 *
 * <p>修改本类时必须同步更新 {@link ScoringSectionLocatorTest} 的测试用例。
 */
final class ScoringSectionAliases {

    private ScoringSectionAliases() {
    }

    /**
     * 评分标准章节标题的全部别名（按业务常见度排序）。
     * 顺序即 Prompt 展示顺序，应保持稳定便于人工校对。
     *
     * <p>覆盖范围：
     * <ul>
     *   <li>评标办法 / 评标方法 - 最常见的章节标题</li>
     *   <li>评分标准 / 评分细则 - 直接点明评分</li>
     *   <li>评审因素 / 评审标准 - 侧重"评审"维度</li>
     *   <li>评标办法前附表 - 部分招标文件的评分表附件</li>
     *   <li>打分标准 / 评分办法 - 变体说法</li>
     * </ul>
     */
    static final List<String> ALL = List.of(
            "评标办法",
            "评分标准",
            "评审因素",
            "评标方法",
            "评分细则",
            "评审标准",
            "评标办法前附表",
            "打分标准",
            "评分办法"
    );

    /**
     * 用于注入 Prompt 的展示文案："评标办法/评分标准/..."。
     */
    static final String DISPLAY = String.join("/", ALL);
}
