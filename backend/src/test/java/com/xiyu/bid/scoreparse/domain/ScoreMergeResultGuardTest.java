// Input: ScoreMergeResultGuard（guard 方法）
// Output: 归并裁决结果双向防御行为验证（spec 055 US1 / 边界情况）
// Pos: Test/scoreparse/domain

package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreMergeResultGuardTest {

    private static final String SECTION_TEXT = """
            价格部分：
            不平衡报价评审：评标委员会针对各投标人分项报价中的单项折扣率进行评价，报价合理得4-5分。5分
            投标人提供上架商品价格管控方案：重点阐述商品定价策略。优秀得4分。4分
            技术方案：方案完整可行得10分。注：投标人须自行提供相关承诺。
            """;

    private ScoreCandidate poolItem(String detail, String weight) {
        return new ScoreCandidate("", "价格部分", detail,
                weight == null ? null : new BigDecimal(weight), null, null, detail, "P47", null);
    }

    @Test
    void guard_hallucinatedDetailDropped_backedDetailKept() {
        List<ScoreCandidate> merged = List.of(
                new ScoreCandidate("", "价格部分",
                        "不平衡报价评审：评标委员会针对各投标人分项报价中的单项折扣率进行评价，报价合理得4-5分。",
                        new BigDecimal("5"), null, null, "原文", "P47", null),
                new ScoreCandidate("", "价格部分",
                        "AI编造的评分项：完全不存在的条款内容得9分。",
                        new BigDecimal("9"), null, null, "原文", "P47", null));
        ScoreMergeResultGuard.Outcome outcome = ScoreMergeResultGuard.guard(
                List.of(poolItem("不平衡报价评审：评标委员会针对各投标人分项报价中的单项折扣率进行评价，报价合理得4-5分。", "5")),
                merged, SECTION_TEXT);
        assertThat(outcome.accepted()).hasSize(1);
        assertThat(outcome.accepted().get(0).detail()).contains("不平衡报价评审");
        assertThat(outcome.hallucinationDropped()).isEqualTo(1);
    }

    @Test
    void guard_highConfidencePoolItemMissingFromMerge_restored() {
        // LLM 归并漏掉了"上架商品价格管控方案"（有原文背书 + weight 非空）→ 回补
        List<ScoreCandidate> merged = List.of(
                new ScoreCandidate("", "价格部分",
                        "不平衡报价评审：评标委员会针对各投标人分项报价中的单项折扣率进行评价，报价合理得4-5分。",
                        new BigDecimal("5"), null, null, "原文", "P47", null));
        List<ScoreCandidate> pool = List.of(
                poolItem("不平衡报价评审：评标委员会针对各投标人分项报价中的单项折扣率进行评价，报价合理得4-5分。", "5"),
                poolItem("投标人提供上架商品价格管控方案：重点阐述商品定价策略。优秀得4分。", "4"),
                poolItem("无背书低置信项", null));
        ScoreMergeResultGuard.Outcome outcome = ScoreMergeResultGuard.guard(pool, merged, SECTION_TEXT);
        assertThat(outcome.accepted()).hasSize(2);
        assertThat(outcome.accepted().get(1).detail()).contains("上架商品价格管控方案");
        assertThat(outcome.restoredCount()).isEqualTo(1);
    }

    @Test
    void guard_mergedDetailSupersetOfPoolItem_coversIt() {
        // 归并输出 detail 是池项 detail 的超集（更完整版本）→ 视为已覆盖，不回补
        List<ScoreCandidate> merged = List.of(
                new ScoreCandidate("", "技术方案",
                        "技术方案：方案完整可行得10分。注：投标人须自行提供相关承诺。",
                        new BigDecimal("10"), null, null, "原文", "P47", null));
        ScoreMergeResultGuard.Outcome outcome = ScoreMergeResultGuard.guard(
                List.of(poolItem("技术方案：方案完整可行得10分。", "10")), merged, SECTION_TEXT);
        assertThat(outcome.accepted()).hasSize(1);
        assertThat(outcome.restoredCount()).isZero();
    }

    @Test
    void guard_mergedOutputInternalDuplicates_deduped() {
        // M3/SC-2：LLM 归并输出自身互相包含（重复未消干净）→ 保留首个，权重不双计
        String detail = "投标人提供上架商品价格管控方案：重点阐述商品定价策略。优秀得4分。";
        List<ScoreCandidate> merged = List.of(
                new ScoreCandidate("", "价格部分", detail,
                        new BigDecimal("4"), null, null, "原文", "P47", "GLOBAL_MERGE"),
                new ScoreCandidate("", "价格部分", detail + "未提供不得分。",
                        new BigDecimal("4"), null, null, "原文", "P47", "GLOBAL_MERGE"));
        ScoreMergeResultGuard.Outcome outcome = ScoreMergeResultGuard.guard(
                List.of(), merged, SECTION_TEXT);
        assertThat(outcome.accepted()).hasSize(1);
        assertThat(outcome.hallucinationDropped()).isEqualTo(1);
    }

    @Test
    void guard_nullOrEmptyInputs_handled() {
        assertThat(ScoreMergeResultGuard.guard(null, List.of(), SECTION_TEXT).accepted()).isEmpty();
        assertThat(ScoreMergeResultGuard.guard(List.of(poolItem("x", "1"), poolItem("y", "2")), null, SECTION_TEXT)
                .accepted()).isEmpty();
        assertThat(ScoreMergeResultGuard.guard(List.of(poolItem("x", "1")), List.of(), null).accepted()).isEmpty();
    }

    @Test
    void guard_highConfidencePoolItemWithWeightOver100_notRestored() {
        // 异常大数防护：候选池中有原文背书但 weight > 100（如误提金额/年份）→ 不视为高置信项，不予回补
        List<ScoreCandidate> pool = List.of(
                poolItem("技术方案：方案完整可行得10分。注：投标人须自行提供相关承诺。", "2024"));
        ScoreMergeResultGuard.Outcome outcome = ScoreMergeResultGuard.guard(pool, List.of(), SECTION_TEXT);
        assertThat(outcome.accepted()).isEmpty();
        assertThat(outcome.restoredCount()).isZero();
    }

    // ── spec 057 FR-02：语料截断场景，池身份匹配的归并输出放行并计数 ──

    @Test
    void guard_poolMatchedOutputOutsideCorpus_keptAsPoolBacked() {
        // 技术项真实存在但评分章节语料截断后不含它 → 不再误判幻觉，放行计 poolBackedKept
        ScoreCandidate poolTech = new ScoreCandidate("", "技术部分",
                "技术方案完整性审查：方案须覆盖全部招标需求点。8分",
                new BigDecimal("8"), null, null,
                "技术方案完整性审查：方案须覆盖全部招标需求点。8分", "P52", null);
        List<ScoreCandidate> merged = List.of(
                new ScoreCandidate("", "技术部分",
                        "技术方案完整性审查：方案须覆盖全部招标需求点。",
                        new BigDecimal("8"), null, null, "原文", "P52", null));
        ScoreMergeResultGuard.Outcome outcome = ScoreMergeResultGuard.guard(
                List.of(poolItem("不平衡报价评审：评标委员会针对各投标人分项报价中的单项折扣率进行评价，报价合理得4-5分。", "5"),
                        poolTech),
                merged, SECTION_TEXT);
        assertThat(outcome.accepted()).hasSize(2); // 池背书放行1 + 语料内高置信池项回补1
        assertThat(outcome.accepted()).extracting(ScoreCandidate::detail)
                .contains("技术方案完整性审查：方案须覆盖全部招标需求点。",
                        "不平衡报价评审：评标委员会针对各投标人分项报价中的单项折扣率进行评价，报价合理得4-5分。");
        assertThat(outcome.poolBackedKept()).isEqualTo(1);
        assertThat(outcome.hallucinationDropped()).isZero();
        assertThat(outcome.restoredCount()).isEqualTo(1);
    }

    @Test
    void guard_poolAbsentDetail_stillHallucination() {
        List<ScoreCandidate> merged = List.of(
                new ScoreCandidate("", "价格部分", "AI 编造：宇宙无敌超级条款得9分。",
                        new BigDecimal("9"), null, null, "原文", "P47", null));
        ScoreMergeResultGuard.Outcome outcome = ScoreMergeResultGuard.guard(
                List.of(poolItem("不平衡报价评审：评标委员会针对各投标人分项报价中的单项折扣率进行评价，报价合理得4-5分。", "5")),
                merged, SECTION_TEXT);
        // 幻觉被规则1丢弃；语料内高置信池项由规则2合法回补——两者计数互不污染
        assertThat(outcome.accepted()).hasSize(1);
        assertThat(outcome.hallucinationDropped()).isEqualTo(1);
        assertThat(outcome.poolBackedKept()).isZero();
        assertThat(outcome.restoredCount()).isEqualTo(1);
    }

    @Test
    void guard_proceduralNoticeCandidates_notRestored() {
        // 前附表程序性条款（采购预备会、开启程序、履约保证金等）被 LLM 显式丢弃后，守卫坚决不予回补
        ScoreCandidate proceduralItem = new ScoreCandidate("1.8", "采购预备会",
                "不召开采购预备会", new BigDecimal("2"), null, null,
                "| 1.8 | 采购预备会 | 2 | 不召开采购预备会 |", "P10", "TABLE_ROW");
        ScoreCandidate validItem = new ScoreCandidate("3.3(1)", "商务评分标准",
                "不平衡报价评审：评标委员会针对各投标人分项报价中的单项折扣率进行评价，报价合理得4-5分。",
                new BigDecimal("5"), null, null, "原文", "P47", null);

        ScoreMergeResultGuard.Outcome outcome = ScoreMergeResultGuard.guard(
                List.of(proceduralItem),
                List.of(validItem),
                SECTION_TEXT);

        assertThat(outcome.accepted()).hasSize(1);
        assertThat(outcome.accepted().get(0).dim()).isEqualTo("商务评分标准");
        assertThat(outcome.restoredCount()).isZero();
    }

    @Test
    void guard_summaryRowCandidates_notRestored() {
        // 分值构成等总览说明行被 LLM 丢弃后，守卫坚决不予回补
        ScoreCandidate summaryItem = new ScoreCandidate("3.1", "分值构成",
                "商务评分标准56分，技术评分标准34分，价格10分", new BigDecimal("100"), null, null,
                "| 3.1 | 分值构成（总分100分） | 商务评分标准56分，技术评分标准34分，价格10分 |", "P12", "TABLE_ROW");
        ScoreCandidate validItem = new ScoreCandidate("3.3(1)", "商务评分标准",
                "不平衡报价评审：评标委员会针对各投标人分项报价中的单项折扣率进行评价，报价合理得4-5分。",
                new BigDecimal("5"), null, null, "原文", "P47", null);

        ScoreMergeResultGuard.Outcome outcome = ScoreMergeResultGuard.guard(
                List.of(summaryItem),
                List.of(validItem),
                SECTION_TEXT);

        assertThat(outcome.accepted()).hasSize(1);
        assertThat(outcome.restoredCount()).isZero();
    }
}
