package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.ArtifactMeta;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable.CanonicalCellDto;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 项目 1391（国网办公类物资）回归：评分办法正文指向「前附表附件一」，权重按 40%/50%/10% 构成。
 * 修复前：正文 6.3 评审办法只有 3 行进入 IN_SCHEME，附件一整段不在任何作用域内，模型拿不到真实评分表；
 * 且原文没有「总分 100 分」正文句（只有前附表条款行），总分只能由权重表闭合推导。
 */
class Project1391ScopeWeightClosureTest {

    private static final String WEIGHT_SECTION = "第二章  响应供应商须知 > 一、分值构成（权重）";

    private static final String TEXT = String.join("\n",
            "# 第二章 响应供应商须知",
            "## 响应供应商须知前附表",
            "| 6.7.1 | 分值权重构成  (总分100 分) | 详见响应供应商须知前附表之附表一。 |",
            "## 响应供应商须知前附表之附表一",
            "**一、分值构成（权重）**",
            "| 序号 | 分标名称 | 技术 | 价格 | 商务 |",
            "| 1 | 办公类用品 | 40% | 50% | 10% |",
            "## 响应供应商须知前附表附件一",
            "**评审办法**",
            "| 评审要素 | 评审内容 |",
            "| 技术评分（100分） | 技术方案可行性 |",
            "| 商务评分（100分） | 商务条款响应 |",
            "| 报价评分（100分） | 价格计算公式 |",
            "## 响应供应商须知前附表附件二",
            "**谈判和再报价流程**",
            "1. 谈判和再报价时间",
            "### 6.3 评审办法",
            "本次评审采用综合评估法。",
            "### 6.4 推荐成交的限制",
            "采购人根据合同包划分作出规定。");

    private static final String MINIMAL_TEXT = String.join("\n",
            "# 第二章 响应供应商须知",
            "### 6.3 评审办法",
            "本次评审采用综合评估法。");

    @Test void appendixOneEntersSchemeScope() {
        int detailLine = lineOf("报价评分（100分）");
        assertThat(OriginalScoringContext.textScopeAt(TEXT, detailLine).scopeStatus()).isEqualTo("IN_SCHEME");
        int appendixTwo = lineOf("谈判和再报价流程");
        assertThat(OriginalScoringContext.textScopeAt(TEXT, appendixTwo).scopeStatus()).isEqualTo("OUT_OF_SCHEME");
    }

    @Test void weightCompositionClosesDocumentTotal() {
        var context = OriginalScoringContext.resolve(artifact(), TEXT);
        assertThat(context.total().value()).isEqualByComparingTo("100");
        assertThat(context.total().source()).isEqualTo("WEIGHT_CLOSURE");
        assertThat(context.total().locator()).contains("t-weights");
        assertThat(context.total().problems()).isEmpty();
    }

    @Test void categoryWeightsCloseSchemeTotalWithoutLeafDoubleCounting() {
        var artifact = artifact();
        var context = OriginalScoringContext.resolve(artifact, TEXT);
        var facts = List.of(
                fact("技术", "", "40", ChildAggregation.UNKNOWN, "技术评分（100分）", 10),
                fact("技术.1", "技术", "100", ChildAggregation.SUM, "技术方案可行性", 10),
                fact("商务", "", "10", ChildAggregation.UNKNOWN, "商务评分（100分）", 11),
                fact("商务.1", "商务", "100", ChildAggregation.SUM, "商务条款响应", 11),
                fact("价格", "", "50", ChildAggregation.UNKNOWN, "报价评分（100分）", 12),
                fact("价格.1", "价格", "100", ChildAggregation.SUM, "价格计算公式", 12));
        var report = new MultimodalScoreFactValidator().validate(facts, artifact, context, TEXT, new BigDecimal("100"));
        assertThat(report.violations()).noneMatch(v -> "WEIGHT_SUM_MISMATCH".equals(v.code()));
    }

    @Test void dimensionWordsInDataRowDoNotCountAsWeightHeader() {
        var table = new CanonicalTable("t-fake", 1, WEIGHT_SECTION, 3, 4, List.of(), List.of(
                cell("f-r0-c0", 0, 0, "序号"), cell("f-r0-c1", 0, 1, "分标名称"),
                cell("f-r0-c2", 0, 2, "技术"), cell("f-r0-c3", 0, 3, "价格"),
                cell("f-r1-c0", 1, 0, "1"), cell("f-r1-c1", 1, 1, "报价说明"),
                cell("f-r1-c2", 1, 2, "100%"), cell("f-r1-c3", 1, 3, "含税"),
                cell("f-r2-c0", 2, 0, "2"), cell("f-r2-c1", 2, 1, "办公类用品"),
                cell("f-r2-c2", 2, 2, "40%"), cell("f-r2-c3", 2, 3, "60%")),
                false);
        var artifact = new DocumentArtifact(new ArtifactMeta("project-1391", "sha256-1391", "docx", "2.2"),
                List.of(table), List.of(), List.of(), "2.2");
        var context = OriginalScoringContext.resolve(artifact, MINIMAL_TEXT);
        assertThat(context.scheme()).contains("评审办法");
        assertThat(context.total().value()).isNull();
    }

    private static MultimodalScoreFact fact(String code, String parent, String weight, ChildAggregation aggregation,
                                            String quote, int line) {
        String schemeId = OriginalScoringContext.schemeId("6.3 评审办法");
        return new MultimodalScoreFact("附件一 评审办法", code, parent, "评分项" + code, new BigDecimal(weight),
                quote, "OBJECTIVE", 1, "", List.of("text:line[" + line + "]"), quote, 0.9,
                aggregation, schemeId, "IN_SCHEME");
    }

    private static int lineOf(String part) {
        String[] lines = TEXT.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) if (lines[i].contains(part)) return i;
        throw new AssertionError("missing line: " + part);
    }

    static DocumentArtifact artifact() {
        return new DocumentArtifact(new ArtifactMeta("project-1391", "sha256-1391", "docx", "2.2"),
                List.of(weightTable(), scoreTable()), List.of(), List.of(), "2.2");
    }

    private static CanonicalTable weightTable() {
        return new CanonicalTable("t-weights", 1, WEIGHT_SECTION, 2, 5, List.of(), List.of(
                cell("w-r0-c0", 0, 0, "序号"), cell("w-r0-c1", 0, 1, "分标名称"), cell("w-r0-c2", 0, 2, "技术"),
                cell("w-r0-c3", 0, 3, "价格"), cell("w-r0-c4", 0, 4, "商务"),
                cell("w-r1-c0", 1, 0, "1"), cell("w-r1-c1", 1, 1, "办公类用品"),
                cell("w-r1-c2", 1, 2, "40%"), cell("w-r1-c3", 1, 3, "50%"), cell("w-r1-c4", 1, 4, "10%")),
                false);
    }

    private static CanonicalTable scoreTable() {
        return new CanonicalTable("t-tech", 2, WEIGHT_SECTION, 2, 2, List.of(), List.of(
                cell("s-r0-c0", 0, 0, "评审要素"), cell("s-r0-c1", 0, 1, "评审内容"),
                cell("s-r1-c0", 1, 0, "技术评分（100分）"), cell("s-r1-c1", 1, 1, "技术方案可行性")),
                true);
    }

    private static CanonicalCellDto cell(String id, int row, int column, String text) {
        return new CanonicalCellDto(id, row, column, text, text, 1, 1, false, "fixture:" + id);
    }
}
