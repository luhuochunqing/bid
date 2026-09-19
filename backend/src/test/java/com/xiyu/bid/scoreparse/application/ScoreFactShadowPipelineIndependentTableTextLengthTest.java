// Input: 两张独立评分表（技术 30/20/10 声明 60 分 + 商务 20/20 声明 40 分）的 DocumentArtifact + 无关正文
// Output: 文档级与分表级完整性决策、正式候选序列（编号/分值/来源逐项断言）
// Pos: scoreparse/application — 独立表完整性不得使用整份文档正文长度做数量启发式（分表完整性误判回归）
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.ScoreCompletenessPolicy;
import com.xiyu.bid.scoreparse.domain.ScoreParseDecision;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 回归 A：无关正文长度不得影响合法独立评分表。
 * <p>技术表（3 项 30+20+10，声明 60 分）与商务表（2 项 20+20，声明 40 分）均结构、
 * 证据、声明与合计完整。整份招标文件正文长度（300/301/10000 字）只属于文档级上下文，
 * 不得作为单张独立评分表"评分项数量异常偏低"的判据——否则 2 项商务表永远被误判漏项。
 */
class ScoreFactShadowPipelineIndependentTableTextLengthTest {

    private static final String IRRELEVANT_FILLER =
            "本项目采用综合评分法，评标委员会按照招标文件规定的各项因素进行评审，"
                    + "供应商应严格按照采购文件要求编制响应文件，并在规定时间内递交至指定地点。";

    @ParameterizedTest(name = "整份无关正文 {0} 字时，两张独立评分表均应完整通过且候选保持 5 项 100 分")
    @ValueSource(ints = {300, 301, 10000})
    @DisplayName("回归A：无关正文长度不影响合法独立评分表（300/301/10000 字结果一致）")
    void independentTables_passRegardlessOfDocumentTextLength(int textLength) {
        String tenderText = buildText(textLength);
        DocumentArtifact artifact = twoTableArtifact();

        ScoreFactShadowPipeline.ShadowResult shadow = ScoreFactShadowPipeline.run(artifact, tenderText);

        assertThat(shadow.tables()).hasSize(2);
        assertThat(shadow.tableCompletenesses()).hasSize(2);
        assertThat(shadow.tableCompletenesses())
                .filteredOn(t -> "tech-t1".equals(t.tableId()))
                .singleElement()
                .satisfies(t -> assertThat(t.completeness().decision())
                        .as("技术表（3 项，声明 60 分）应通过")
                        .isEqualTo(ScoreParseDecision.AUTO_APPROVED));
        assertThat(shadow.tableCompletenesses())
                .filteredOn(t -> "biz-t2".equals(t.tableId()))
                .singleElement()
                .satisfies(t -> {
                    assertThat(t.completeness().decision())
                            .as("商务表（2 项，声明 40 分且合计自洽）不应因正文长被误判 ABNORMALLY_LOW_COUNT")
                            .isEqualTo(ScoreParseDecision.AUTO_APPROVED);
                    assertThat(t.completeness().fallbackReason())
                            .as("不应出现数量异常拦截")
                            .isNotEqualTo("ABNORMALLY_LOW_COUNT");
                    assertThat(t.completeness().declaredTotalSource())
                            .as("独立表必须使用本表 40 分声明，而非整份文档兜底总分")
                            .isEqualTo(ScoreCompletenessPolicy.DeclaredTotalSource.STRUCTURED_SUMMARY_ROW);
                });

        // 文档级聚合：全表自洽 → 通过
        assertThat(shadow.completeness().decision()).isEqualTo(ScoreParseDecision.AUTO_APPROVED);

        // 正式候选逐项断言：编号/分值/来源（不能只断言总分）
        List<ScoreCandidateView> leaves = shadow.tableCompletenesses().stream()
                .filter(t -> t.completeness() != null
                        && t.completeness().decision() == ScoreParseDecision.AUTO_APPROVED)
                .flatMap(t -> t.completeness().leafFacts().stream())
                .map(f -> new ScoreCandidateView(f.factId(), f.code(), f.weight(), f.sourceTableId()))
                .toList();
        assertThat(leaves).hasSize(5);
        assertThat(leaves).extracting(ScoreCandidateView::sourceTableId)
                .containsExactly("tech-t1", "tech-t1", "tech-t1", "biz-t2", "biz-t2");
        assertThat(leaves).extracting(ScoreCandidateView::code)
                .containsExactly("1", "2", "3", "1", "2");
        assertThat(leaves).extracting(v -> v.weight().stripTrailingZeros().toPlainString())
                .containsExactly("30", "20", "10", "20", "20");
        assertThat(leaves.stream().map(ScoreCandidateView::weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
                .as("两张表 5 项候选合计必须为 100 分")
                .isEqualByComparingTo("100");
        // 文档级聚合取最差决策表的明细，分表权重各自自洽（60/40）
        assertThat(shadow.tableCompletenesses())
                .filteredOn(t -> "tech-t1".equals(t.tableId()))
                .singleElement()
                .extracting(t -> t.completeness().leafWeightSum())
                .isEqualTo(BigDecimal.valueOf(60).setScale(2));
        assertThat(shadow.tableCompletenesses())
                .filteredOn(t -> "biz-t2".equals(t.tableId()))
                .singleElement()
                .extracting(t -> t.completeness().leafWeightSum())
                .isEqualTo(BigDecimal.valueOf(40).setScale(2));
    }

    @Test
    @DisplayName("回归A边界：商务表自身细则超 300 字且仅 2 个合法评分项，结构/证据/40分声明完整 → 不误判漏项")
    void commercialTable_longOwnRulesButTwoLegitimateItems_passes() {
        // 正文包含商务表细则全文（>300 字）：模拟"该表细则写得长"，不应触发数量拦截
        String commercialRules = "商务报价部分：投标报价以经评审的投标价为依据，报价合理的得满分20分。"
                + "售后服务部分：服务方案完善、承诺响应时间不超过2小时、提供本地化服务团队、"
                + "备品备件供应及时、培训方案可行的得满分20分。供应商须提供相应的证明材料，"
                + "未提供或提供不全的酌情扣分。评分细则依据招标文件第三章评标办法执行。"
                + IRRELEVANT_FILLER.repeat(3);
        assertThat(commercialRules.trim().length()).isGreaterThan(300);

        DocumentArtifact artifact = twoTableArtifact();
        ScoreFactShadowPipeline.ShadowResult shadow =
                ScoreFactShadowPipeline.run(artifact, commercialRules);

        assertThat(shadow.tableCompletenesses())
                .filteredOn(t -> "biz-t2".equals(t.tableId()))
                .singleElement()
                .satisfies(t -> assertThat(t.completeness().decision())
                        .as("细则写得长但仅 2 项且 40 分声明与合计自洽，不应误判漏项")
                        .isEqualTo(ScoreParseDecision.AUTO_APPROVED));
    }

    @Test
    @DisplayName("回归B：商务表声明 40 分但仅抽取到 20 分 → 必须复核，不得自动通过")
    void commercialTable_declared40ButOnly20ItemsExtracted_requiresReview() {
        List<List<String>> techRows = List.of(
                List.of("序号", "评分因素", "评分标准"),
                List.of("1", "技术方案\n（30分）", "方案优秀得满分30分。"),
                List.of("2", "技术团队\n（20分）", "团队优秀得满分20分。"),
                List.of("3", "技术培训\n（10分）", "培训完整得满分10分。"),
                List.of("合计\n得分", "满分\n（60分）", ""));
        // 漏抽一行：声明 40 分，实际只有 1 项 20 分
        List<List<String>> bizRows = List.of(
                List.of("序号", "评分因素", "评分标准"),
                List.of("1", "商务报价\n（20分）", "报价合理得满分20分。"),
                List.of("合计\n得分", "满分\n（40分）", ""));

        DocumentArtifact artifact = new DocumentArtifact(
                new DocumentArtifact.ArtifactMeta("sha256:partial", "partial", "PDF", "pdf-table-v1"),
                List.of(scoreTable("tech-t1", techRows), scoreTable("biz-t2", bizRows)),
                List.of());
        ScoreFactShadowPipeline.ShadowResult shadow =
                ScoreFactShadowPipeline.run(artifact, buildText(301));

        assertThat(shadow.tableCompletenesses())
                .filteredOn(t -> "biz-t2".equals(t.tableId()))
                .singleElement()
                .satisfies(t -> {
                    assertThat(t.completeness().decision())
                            .as("真正漏项（合计 20 != 声明 40）必须复核")
                            .isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
                    assertThat(t.completeness().fallbackReason()).isEqualTo("WEIGHT_SUM_MISMATCH");
                });
        assertThat(shadow.completeness().decision()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
    }

    /** 双表夹具：技术 60 分表 + 商务 40 分表，全部行带分值证据与原文单元格。 */
    private static DocumentArtifact twoTableArtifact() {
        List<List<String>> techRows = List.of(
                List.of("序号", "评分因素", "评分标准"),
                List.of("1", "技术方案\n（30分）", "方案优秀得满分30分。"),
                List.of("2", "技术团队\n（20分）", "团队优秀得满分20分。"),
                List.of("3", "技术培训\n（10分）", "培训完整得满分10分。"),
                List.of("合计\n得分", "满分\n（60分）", ""));
        List<List<String>> bizRows = List.of(
                List.of("序号", "评分因素", "评分标准"),
                List.of("1", "商务报价\n（20分）", "报价合理得满分20分。"),
                List.of("2", "售后服务\n（20分）", "服务完善得满分20分。"),
                List.of("合计\n得分", "满分\n（40分）", ""));
        return new DocumentArtifact(
                new DocumentArtifact.ArtifactMeta("sha256:two-table", "two-table", "PDF", "pdf-table-v1"),
                List.of(scoreTable("tech-t1", techRows), scoreTable("biz-t2", bizRows)),
                List.of());
    }

    private static DocumentArtifact.CanonicalTable scoreTable(String tableId, List<List<String>> rows) {
        List<DocumentArtifact.CanonicalTable.CanonicalCellDto> cells = new ArrayList<>();
        for (int row = 0; row < rows.size(); row++) {
            for (int col = 0; col < rows.get(row).size(); col++) {
                String text = rows.get(row).get(col);
                cells.add(new DocumentArtifact.CanonicalTable.CanonicalCellDto(
                        tableId + "-r" + row + "-c" + col, row, col, text,
                        text.replaceAll("\\s+", " ").trim(), 1, 1, false,
                        "pdf:page=1;table=" + tableId + ";row=" + row + ";col=" + col));
            }
        }
        return new DocumentArtifact.CanonicalTable(
                tableId, 1, "", rows.size(), rows.get(0).size(), List.of(), cells, true);
    }

    /** 构造指定字数的无关正文（不含任何可解析为总分声明的"XX分"措辞，避免干扰声明总分兜底）。 */
    private static String buildText(int targetLength) {
        StringBuilder sb = new StringBuilder();
        sb.append("第一章 采购邀请。".repeat(2));
        while (sb.length() < targetLength) {
            sb.append(IRRELEVANT_FILLER);
        }
        return sb.substring(0, targetLength);
    }

    /** 逐项断言用的候选投影（编号/分值/来源）。 */
    private record ScoreCandidateView(String factId, String code, BigDecimal weight, String sourceTableId) {
    }
}
