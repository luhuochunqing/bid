// Input: none (constant holder)
// Output: canonical alias lists for each requirement section in Chinese tender documents
// Pos: biddraftagent/infrastructure/openai - single source of truth for section aliases
package com.xiyu.bid.biddraftagent.infrastructure.openai;

import java.util.List;

/**
 * 招标文件各维度章节标题别名词表。
 *
 * <p>招标文件中不同维度的章节标题有多种叫法，本类是 ProfileEnhancer
 * 和 SectionLocator 的<strong>唯一真相来源</strong>。
 *
 * <p>对标 {@link ScoringSectionAliases} 的设计模式，扩展到 4 个维度：
 * 资质、技术、商务、风险。评分标准别名仍由 {@link ScoringSectionAliases} 维护。
 */
final class SectionAliases {

    private SectionAliases() {
    }

    /** 评分标准别名（委托给 ScoringSectionAliases，保持单一真相来源）。 */
    static final List<String> SCORING = ScoringSectionAliases.ALL;

    /** 资质要求章节标题别名。 */
    static final List<String> QUALIFICATION = List.of(
            "资格要求",
            "资格条件",
            "投标人资格",
            "投标人资格条件",
            "资质要求",
            "资格证明",
            "合格投标人",
            "投标人资质"
    );

    /** 技术要求章节标题别名。 */
    static final List<String> TECHNICAL = List.of(
            "技术要求",
            "技术规范",
            "技术标准",
            "技术参数",
            "技术服务要求",
            "技术方案要求",
            "技术规格"
    );

    /** 商务要求章节标题别名。 */
    static final List<String> COMMERCIAL = List.of(
            "商务要求",
            "商务条款",
            "合同条款",
            "付款方式",
            "交货条件",
            "商务规范",
            "采购商务要求"
    );

    /** 风险/废标条款章节标题别名。 */
    static final List<String> RISK = List.of(
            "风险条款",
            "废标条款",
            "否决条款",
            "无效投标",
            "风险提示",
            "视为无效投标",
            "不予受理"
    );
}
