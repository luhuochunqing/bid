// Input: none (constant holder)
// Output: canonical list of purchaser (招标主体) alias labels used in Chinese tender documents
// Pos: biddraftagent/infrastructure/openai — single source of truth for purchaser alias labels
package com.xiyu.bid.biddraftagent.infrastructure.openai;

import java.util.List;

/**
 * 招标主体别名常量。
 *
 * <p>招标文件中可识别为"招标主体"的字段标签，按业务约定共 7 种：
 * 招标人、招标单位、采购人、采购单位、项目单位、实施单位、需求单位。
 * 另外保留历史口径中的"业主单位"作为兼容别名（共 8 种）。
 *
 * <p>本类是 Prompt 字段口径与候选文本关键词预提取的<strong>唯一真相来源</strong>，
 * 避免两处列表各自维护导致不同步（曾出现 Prompt 列 4 种、Keywords 列 5 种的分歧）。
 *
 * <p>修改本类时必须同步更新 {@link TenderDocumentPrompts} 的 Few-Shot 示例和
 * {@link OpenAiTenderDocumentAnalyzerTest} 的断言。
 */
final class PurchaserAliases {

    private PurchaserAliases() {
    }

    /**
     * 招标主体全部别名标签（按业务约定顺序）。
     * 顺序即 Prompt 字段口径展示顺序，应保持稳定便于人工校对。
     */
    static final List<String> ALL = List.of(
            "招标人",
            "招标单位",
            "采购人",
            "采购单位",
            "项目单位",
            "实施单位",
            "需求单位",
            "业主单位"
    );

    /**
     * 可能作为招标主体出现的"可能标签"（业务确认需保留但非明确）。
     * 出现时且无更明确的招标主体标签，仍应填入 purchaserName。
     * 与 {@link #ALL} 区分：ALL 是"明确是招标主体"，POSSIBLE 是"可能是招标主体"。
     */
    static final List<String> POSSIBLE = List.of(
            "组织单位",
            "主办单位",
            "采购部门"
    );

    /**
     * 用于注入 Prompt 的展示文案："招标人/招标单位/采购人/采购单位/项目单位/实施单位/需求单位/业主单位"。
     */
    static final String DISPLAY = String.join("/", ALL);

    /**
     * "可能标签"展示文案："组织单位/主办单位/采购部门"。
     */
    static final String POSSIBLE_DISPLAY = String.join("/", POSSIBLE);
}
