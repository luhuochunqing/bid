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

    // ── spec 059 Phase 3：移除"池文本互含即放行"，语料外项即使与池项互含也必须判为幻觉丢弃 ──

    @Test
    void guard_poolMatchedOutputOutsideCorpus_droppedAsHallucination_noMutualContainmentBypass() {
        // 技术项真实存在于池中，但 LLM 归并输出项在评分章节原文中找不到 → 坚决判为幻觉丢弃（移除池身份放行）
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
        // LLM 输出项由于不在 SECTION_TEXT 被视为幻觉丢弃；池内高置信项由规则 2 兜底回补
        assertThat(outcome.hallucinationDropped()).isEqualTo(1);
        assertThat(outcome.poolBackedKept()).isZero();
        assertThat(outcome.restoredCount()).isEqualTo(2);
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

    @Test
    void guard_parentCategorySummaryCandidates_notRestoredWhenChildrenExist() {
        // 场景：LLM 采纳了子项 1.1, 1.2, 1.3, 1.4，并丢弃了父大项"公司财务状况：10分"
        // 守卫必须识别到父子关系，禁止将父大项作为漏项回补，杜绝 10分 + 6分 = 16分导致的翻倍与腰斩！
        ScoreCandidate parentItem = new ScoreCandidate("1", "详细评审",
                "公司财务状况：10分", new BigDecimal("10"), null, null,
                "| 1 | 详细评审 | 公司财务状况：10分 |", "P15", "TABLE_ROW");
        ScoreCandidate child1 = new ScoreCandidate("1.1", "公司财务状况",
                "净资产＜2000万元，不得分；2000万元≤净资产＜5000万元，得1分；5000万元≤净资产＜1亿元，得1.5分",
                new BigDecimal("1.5"), null, null, "原文", "P15", "TABLE_ROW");
        ScoreCandidate child2 = new ScoreCandidate("1.2", "公司财务状况",
                "对营业收入＜2.5亿元的不得分，2.5亿元≤营业收入＜5亿元，得1分",
                new BigDecimal("1"), null, null, "原文", "P15", "TABLE_ROW");

        String sectionWithDetails = SECTION_TEXT
                + " 公司财务状况：10分 "
                + " 净资产＜2000万元，不得分；2000万元≤净资产＜5000万元，得1分；5000万元≤净资产＜1亿元，得1.5分 "
                + " 对营业收入＜2.5亿元的不得分，2.5亿元≤营业收入＜5亿元，得1分 ";

        ScoreMergeResultGuard.Outcome outcome = ScoreMergeResultGuard.guard(
                List.of(parentItem, child1, child2),
                List.of(child1, child2),
                sectionWithDetails);

        assertThat(outcome.accepted()).hasSize(2);
        assertThat(outcome.accepted()).extracting(ScoreCandidate::code).containsExactly("1.1", "1.2");
        assertThat(outcome.restoredCount()).isZero();
    }

    @Test
    void guard_pureCategoryHeaderWithoutSubRules_notRestored() {
        // 大项汇总标题如"体系认证：12分"、"业绩：15分"等无打分档位细则的纯分类行，当 accepted 已存在该维度项时坚决不回补
        ScoreCandidate categoryHeader = new ScoreCandidate("2", "详细评审",
                "体系认证：12分", new BigDecimal("12"), null, null,
                "| 2 | 详细评审 | 体系认证：12分 |", "P16", "TABLE_ROW");
        ScoreCandidate validSubItem = new ScoreCandidate("2", "体系认证",
                "具有有效期内的ISO9001质量管理体系认证得4分，ISO14001得4分，ISO45001得4分",
                new BigDecimal("12"), null, null, "原文", "P16", null);

        String sectionWithDetails = SECTION_TEXT
                + " 体系认证：12分 "
                + " 具有有效期内的ISO9001质量管理体系认证得4分，ISO14001得4分，ISO45001得4分 ";

        ScoreMergeResultGuard.Outcome outcome = ScoreMergeResultGuard.guard(
                List.of(categoryHeader, validSubItem),
                List.of(validSubItem),
                sectionWithDetails);

        assertThat(outcome.accepted()).hasSize(1);
        assertThat(outcome.accepted().get(0).detail()).contains("ISO9001");
        assertThat(outcome.restoredCount()).isZero();
    }

    @Test
    void guard_financialDetailNotAbsorbedIntoNeighborItem() {
        // Bug reproduction: lane-4 LLM candidate for 财务状况 carries chunk-level sourceText
        // that contains every merged item's detail. The old sourceMatch mechanism in
        // upgradeWithLongestPoolDetail() would graft 财务状况's long detail onto 信用评价
        // (the longest pool detail wins), then coveredBy() blocks the real 财务状况 from
        // being restored → 财务状况 completely absent from output.
        String financialDetail = "（1）速动比率=速动资产/流动负债。速动比≥1，得50分；0.8≤速动比<1，得40分；速动比<0.8，得30分。数据取值来源：①速动资产＜资产负债表>：（流动资产合计-存货）②流动负债=<资产负债表>流动负债合计（2）营业收现比率=经营活动现金流入/营业总收入。营业收现比率≥1，得50分；0.8≤营业收现比率<1，得40分；营业收现比率<0.8，得30分。";
        String chunkSourceText = "信用评价 2023年1月1日至今，在该网站无不良记录，得100分 " + financialDetail;

        ScoreCandidate poolCredit = new ScoreCandidate("2.2.4(1)", "信用评价",
                "2023年1月1日至今，在该网站无不良记录，得100分；有处罚记录无罚款，得90分；罚款20万以内，得80分。本项满分100分。",
                new BigDecimal("5"), null, null,
                "2023年1月1日至今，在该网站无不良记录，得100分；有处罚记录无罚款，得90分；罚款20万以内，得80分。本项满分100分。",
                "R2", "TABLE_GRID");

        ScoreCandidate poolFinancial = new ScoreCandidate("2.2.4(1)", "财务状况",
                financialDetail,
                new BigDecimal("6"), null, null,
                chunkSourceText, "R4", "TABLE_GRID");

        List<ScoreCandidate> merged = List.of(
                new ScoreCandidate("2.2.4(1)", "信用评价",
                        "2023年1月1日至今，在该网站无不良记录，得100分；有处罚记录无罚款，得90分。",
                        new BigDecimal("5"), null, null, "原文", "P47", "GLOBAL_MERGE"),
                new ScoreCandidate("2.2.4(1)", "财务状况",
                        "速动比率≥1得50分，其余得0分。",
                        new BigDecimal("6"), null, null, "原文", "P47", "GLOBAL_MERGE"));

        String sectionText = "信用评价 2023年1月1日至今，在该网站无不良记录，得100分；有处罚记录无罚款，得90分；罚款20万以内，得80分。本项满分100分。 "
                + financialDetail;

        ScoreMergeResultGuard.Outcome outcome = ScoreMergeResultGuard.guard(
                List.of(poolCredit, poolFinancial), merged, sectionText);

        assertThat(outcome.accepted()).hasSize(2);
        assertThat(outcome.accepted()).extracting(ScoreCandidate::dim)
                .containsExactly("信用评价", "财务状况");
        assertThat(outcome.accepted().get(0).detail())
                .doesNotContain("速动比率");
        assertThat(outcome.accepted().get(1).detail())
                .contains("速动比率")
                .contains("营业收现比率");
    }

    @Test
    void guard_truncatedMergedItem_upgradesToFullPoolDetail() {
        // 当 LLM 归并输出由于截断或提纲化输出了短版 detail 时，守卫自动从池中匹配并升级为最完整的逐字细则
        String fullDetail = "1.售前服务方案完整得3分；2.售中服务方案（含订单状态一致性与出库配送保障）得4分；3.售后服务方案（含退换货与三包服务）得3分。";
        String truncatedDetail = "1.售前服务方案完整得3分；2.售中服务方案（含订单状态一致性与出库";

        ScoreCandidate poolFull = new ScoreCandidate("3.2.3(2)", "技术评分/综合服务方案",
                fullDetail, new BigDecimal("10"), "SUBJECTIVE", null,
                fullDetail, "P30", "TABLE_GRID");

        ScoreCandidate mergedTruncated = new ScoreCandidate("3.2.3(2)", "技术评分/综合服务方案",
                truncatedDetail, new BigDecimal("10"), "SUBJECTIVE", null,
                truncatedDetail, "P30", "GLOBAL_MERGE");

        String sectionText = SECTION_TEXT + "\n" + fullDetail;

        ScoreMergeResultGuard.Outcome outcome = ScoreMergeResultGuard.guard(
                List.of(poolFull),
                List.of(mergedTruncated),
                sectionText);

        assertThat(outcome.accepted()).hasSize(1);
        ScoreCandidate accepted = outcome.accepted().get(0);
        assertThat(accepted.detail()).isEqualTo(fullDetail);
        assertThat(accepted.detail()).contains("3.售后服务方案（含退换货与三包服务）得3分");
    }

    @Test
    void guard_upgradedDetail_preservesParentDimAndDimSource() {
        String fullDetail = "1. 人员配置：项目经理具备一级建造师证书得5分；技术负责人具备中级职称得5分。满分10分。";
        String shortDetail = "1. 人员配置：项目经理具备一级建造师证书得5分";

        ScoreCandidate poolFull = new ScoreCandidate("1", "商务部分",
                fullDetail, new BigDecimal("10"), "OBJECTIVE", null,
                fullDetail, "P1", "TABLE_GRID", "商务部分", "PARENT_SINGLE");

        ScoreCandidate mergedShort = new ScoreCandidate("1", "商务部分",
                shortDetail, new BigDecimal("10"), "OBJECTIVE", null,
                shortDetail, "P1", "GLOBAL_MERGE");

        String sectionText = SECTION_TEXT + "\n" + fullDetail;
        ScoreMergeResultGuard.Outcome outcome = ScoreMergeResultGuard.guard(
                List.of(poolFull), List.of(mergedShort), sectionText);

        assertThat(outcome.accepted()).hasSize(1);
        ScoreCandidate accepted = outcome.accepted().get(0);
        assertThat(accepted.detail()).isEqualTo(fullDetail);
        assertThat(accepted.parentDim()).isEqualTo("商务部分");
        assertThat(accepted.dimSource()).isEqualTo("PARENT_SINGLE");
        assertThat(accepted.isParentOnlyDim()).isTrue();
    }

    @Test
    void guard_sourceTextOverlapAlone_doesNotTriggerUpgrade() {
        // sourceMatch 已从 upgradeWithLongestPoolDetail 移除。
        // 此测试证明：仅 sourceText 重叠不足以触发升级，必须 detail 包含关系成立才升级。
        String sharedSourceText = "信用评价无不良记录得100分 财务状况速动比率≥1得50分";

        ScoreCandidate poolCredit = new ScoreCandidate("2.2.4(1)", "信用评价",
                "无不良记录得100分", new BigDecimal("5"), null, null,
                sharedSourceText, "R2", "TABLE_GRID");

        ScoreCandidate poolFinancial = new ScoreCandidate("2.2.4(2)", "财务状况",
                "速动比率≥1得50分；0.8≤速动比<1，得40分", new BigDecimal("6"), null, null,
                sharedSourceText, "R4", "TABLE_GRID");

        List<ScoreCandidate> merged = List.of(
                new ScoreCandidate("2.2.4(1)", "信用评价",
                        "无不良记录得100分", new BigDecimal("5"), null, null,
                        "原文", "P47", "GLOBAL_MERGE"));

        ScoreMergeResultGuard.Outcome outcome = ScoreMergeResultGuard.guard(
                List.of(poolCredit, poolFinancial), merged, sharedSourceText);

        assertThat(outcome.accepted()).hasSize(1);
        assertThat(outcome.accepted().get(0).detail()).isEqualTo("无不良记录得100分");
        assertThat(outcome.accepted().get(0).detail()).doesNotContain("速动比率");
    }

    @Test
    void guard_poolCandidateEqualLengthDetail_inheritsParentDimAndDimSource() {
        // 真实 1359 场景：LLM 归并保留了完整细则（长度与池项完全一致），原实现漏掉继承 parentDim 与 dimSource
        String detail = "对应答人提供的品牌丰富度进行评价。品牌丰富优秀得4分；良好得2分；一般及以下得1分。";
        ScoreCandidate poolItem = new ScoreCandidate(null, "品牌丰富度", detail,
                new BigDecimal("4"), "OBJECTIVE", null, detail, "R6", "STRUCTURED_TABLE",
                "技术评分标准", "CELL");

        ScoreCandidate merged = new ScoreCandidate(null, "品牌丰富度", detail,
                new BigDecimal("4"), "OBJECTIVE", null, detail, "P1", "GLOBAL_MERGE",
                null, "UNKNOWN");

        ScoreMergeResultGuard.Outcome outcome = ScoreMergeResultGuard.guard(
                List.of(poolItem), List.of(merged), detail);

        assertThat(outcome.accepted()).hasSize(1);
        ScoreCandidate accepted = outcome.accepted().get(0);
        assertThat(accepted.parentDim()).isEqualTo("技术评分标准");
        assertThat(accepted.dimSource()).isEqualTo("CELL");
    }
}
