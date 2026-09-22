package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OriginalScoreEvidenceRoleAndHeadingTest {
    private static final String SCHEME = "本项目评分办法";

    @Test
    void knownAssessmentFactorHeadersProtectItemMaximumFromSchemeTotal() {
        var context = OriginalScoringContext.resolve(detailTable(),
                "# 本项目评分办法\n评分说明如下，详见下表。\n");
        assertThat(context.total().value()).isNull();
        assertThat(context.total().source()).isEqualTo("UNKNOWN");
    }

    @Test
    void unknownHeadersCannotPromoteAnItemMaximumToSchemeTotal() {
        var context = OriginalScoringContext.resolve(detailTableWithHeaders(
                        List.of("字段甲", "字段乙", "字段丙", "字段丁")),
                "# 本项目评分办法\n评分说明如下，详见下表。\n");
        assertThat(context.total().value()).isNull();
    }

    @Test
    void explicitSchemeTotalStillClosesTheSameDetailTable() {
        var facts = List.of(
                fact("1", "销售额", "10", "t-items-r1-c3", "满分10分，销售额达到1亿元得10分。"),
                fact("2", "技术方案", "90", "t-items-r2-c3", "技术方案最高得90分。"));
        var result = new MultimodalScoreFactValidator().validate(facts, detailTable(),
                "# 本项目评分办法\n本项目评分总分100分。\n", new BigDecimal("100"));
        assertThat(result.originalTotal()).isEqualByComparingTo("100");
        assertThat(result.autoApprovable()).isTrue();
    }

    @Test
    void childHeadingInheritsTheParentSchemeForTextTotal() {
        var context = OriginalScoringContext.resolve(detailTable(),
                "# 本项目评分办法\n## 评分说明\n总分100分。\n");
        assertThat(context.total().value()).isEqualByComparingTo("100");
        assertThat(context.total().source()).isEqualTo("TENDER_TEXT");
    }

    @Test
    void unrelatedPeerHeadingCannotSupplyTheParentSchemeTotal() {
        var context = OriginalScoringContext.resolve(detailTable(),
                "# 本项目评分办法\n评分说明如下。\n# 履约考核办法\n总分100分。\n");
        assertThat(context.total().value()).isNull();
        assertThat(context.total().source()).isEqualTo("UNKNOWN");
    }

    private static DocumentArtifact detailTable() {
        return detailTableWithHeaders(List.of("序号", "评审因素", "分值", "评审标准"));
    }

    private static DocumentArtifact detailTableWithHeaders(List<String> headers) {
        List<List<String>> rows = List.of(
                headers,
                List.of("1", "销售额", "10", "满分10分，销售额达到1亿元得10分。"),
                List.of("2", "技术方案", "90", "技术方案最高得90分。"));
        List<DocumentArtifact.CanonicalTable.CanonicalCellDto> cells = new ArrayList<>();
        for (int row = 0; row < rows.size(); row++) {
            for (int column = 0; column < rows.get(row).size(); column++) {
                String value = rows.get(row).get(column);
                cells.add(new DocumentArtifact.CanonicalTable.CanonicalCellDto(
                        "t-items-r" + row + "-c" + column, row, column, value, value,
                        1, 1, false, "pdf:page=1;table=t-items"));
            }
        }
        return new DocumentArtifact(
                new DocumentArtifact.ArtifactMeta("bid.pdf", "sha-role-heading", "pdf", "2.0"),
                List.of(new DocumentArtifact.CanonicalTable("t-items", 1, SCHEME + " / 评分明细",
                        rows.size(), rows.get(0).size(), List.of(), cells, true)), List.of());
    }

    private static MultimodalScoreFact fact(String code, String name, String weight,
                                            String cellId, String rule) {
        return new MultimodalScoreFact(SCHEME, code, "", name, new BigDecimal(weight), rule,
                "OBJECTIVE", 1, "t-items", List.of(cellId), rule, 0.9);
    }
}
