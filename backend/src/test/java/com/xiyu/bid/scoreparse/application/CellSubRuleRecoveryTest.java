package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.ChildAggregation;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFactValidator;
import com.xiyu.bid.scoreparse.domain.OriginalScoringContext;
import com.xiyu.bid.scoreparse.domain.ScoreUnitPolicy;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.ArtifactMeta;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable.CanonicalCellDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CellSubRuleRecoveryTest {

    private static final String CLAUSE_1_DETAIL = """
            1.进行 ICP 备案核查，以工信部备案管理系统（https://beian.miit.gov.cn）公开查询结果为准，供应商须提供工信部 ICP 备案管理系统官网实时查询截图，提供截图得5分，无截图不得分。 2.投标人近三年信用良好，且无被诉讼记录，得5分，有被诉讼记录不得分。（提供“信用中国”官网截图证明，截图界面见综合评分办法-企业信用截图范例）""";

    @Test
    @DisplayName("当父项分值10分、大模型只抽取出细则1(5分)导致差额时，确定性自动从单元格中补齐细则2(5分)")
    void shouldRecoverMissingSubRuleWhenParentWeightDeficitExists() {
        CanonicalCellDto headerCode = new CanonicalCellDto("c-0-0", 0, 0, "条款号", "条款号", 1, 1, false, "pdf:page=13", 13, "r-0");
        CanonicalCellDto headerName = new CanonicalCellDto("c-0-1", 0, 1, "评审因素", "评审因素", 1, 1, false, "pdf:page=13", 13, "r-0");
        CanonicalCellDto headerRule = new CanonicalCellDto("c-0-2", 0, 2, "评审标准", "评审标准", 1, 1, false, "pdf:page=13", 13, "r-0");
        CanonicalCellDto headerWeight = new CanonicalCellDto("c-0-3", 0, 3, "分值", "分值", 1, 1, false, "pdf:page=13", 13, "r-0");

        CanonicalCellDto row1Code = new CanonicalCellDto("c-1-0", 1, 0, "1", "1", 1, 1, false, "pdf:page=13", 13, "r-1");
        CanonicalCellDto row1Name = new CanonicalCellDto("c-1-1", 1, 1, "供应商合规性评审及企业信用(10分）", "供应商合规性评审及企业信用", 1, 1, false, "pdf:page=13", 13, "r-1");
        CanonicalCellDto row1Rule = new CanonicalCellDto("c-1-2", 1, 2, CLAUSE_1_DETAIL, CLAUSE_1_DETAIL, 1, 1, false, "pdf:page=13", 13, "r-1");
        CanonicalCellDto row1Weight = new CanonicalCellDto("c-1-3", 1, 3, "10", "10", 1, 1, false, "pdf:page=13", 13, "r-1");

        CanonicalTable table = new CanonicalTable("t1", 13, "第四章 评审办法", 2, 4, List.of(),
                List.of(headerCode, headerName, headerRule, headerWeight, row1Code, row1Name, row1Rule, row1Weight),
                true, "PDF");
        DocumentArtifact artifact = new DocumentArtifact(new ArtifactMeta("doc-1", "sha", "PDF", "2.2"), List.of(table), List.of());

        MultimodalScoreFact parent = new MultimodalScoreFact(
                "第四章 评审办法", "1", "", "供应商合规性评审及企业信用",
                new BigDecimal("10.00"), "", "SUBJECTIVE", 13, "t1",
                List.of("c-1-1"), "供应商合规性评审及企业信用(10分）", 0.95,
                ChildAggregation.SUM, "scheme-1", "IN_SCHEME"
        );
        MultimodalScoreFact child1 = new MultimodalScoreFact(
                "第四章 评审办法", "1.1", "1", "ICP备案核查",
                new BigDecimal("5.00"),
                "1.进行 ICP 备案核查，以工信部备案管理系统（https://beian.miit.gov.cn）公开查询结果为准，供应商须提供工信部 ICP 备案管理系统官网实时查询截图，提供截图得5分，无截图不得分。",
                "OBJECTIVE", 13, "t1",
                List.of("c-1-2"),
                "1.进行 ICP 备案核查，以工信部备案管理系统（https://beian.miit.gov.cn）公开查询结果为准，供应商须提供工信部 ICP 备案管理系统官网实时查询截图，提供截图得5分，无截图不得分。",
                0.95,
                ChildAggregation.SUM, "scheme-1", "IN_SCHEME"
        );

        List<MultimodalScoreFact> initialFacts = List.of(parent, child1);
        List<MultimodalScoreFact> recovered = CellSubRuleRecovery.resolve(initialFacts, artifact);

        assertThat(recovered).hasSize(3);
        MultimodalScoreFact child2 = recovered.stream()
                .filter(f -> "1.2".equals(f.code()))
                .findFirst().orElse(null);
        assertThat(child2).isNotNull();
        assertThat(child2.parentCode()).isEqualTo("1");
        assertThat(child2.weight()).isEqualByComparingTo("5.00");
        assertThat(child2.dimension()).isEqualTo("投标人近三年信用良好");
        assertThat(child2.ruleText()).contains("无被诉讼记录，得5分");
        assertThat(child2.tableId()).isEqualTo("t1");
        assertThat(child2.sourceCellIds()).contains("c-1-2");
        assertThat(child2.scopeStatus()).isEqualTo("IN_SCHEME");

        // 验证计算：子项之和 5 + 5 = 10，完全闭合父项 10 分
        List<ScoreUnitPolicy.Node> nodes = recovered.stream()
                .map(f -> new ScoreUnitPolicy.Node(f.code(), f.parentCode(), f.weight(), f.childAggregation().name()))
                .toList();
        assertThat(ScoreUnitPolicy.totalWeight(nodes)).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("当子项已经覆盖父项声明分值时，不重复回补")
    void shouldNotRecoverWhenChildrenAlreadyCoverParentWeight() {
        CanonicalCellDto row1Rule = new CanonicalCellDto("c-1-2", 1, 2, CLAUSE_1_DETAIL, CLAUSE_1_DETAIL, 1, 1, false, "pdf:page=13", 13, "r-1");
        CanonicalTable table = new CanonicalTable("t1", 13, "第四章 评审办法", 2, 4, List.of(),
                List.of(row1Rule), true, "PDF");
        DocumentArtifact artifact = new DocumentArtifact(new ArtifactMeta("doc-1", "sha", "PDF", "2.2"), List.of(table), List.of());

        MultimodalScoreFact parent = new MultimodalScoreFact(
                "第四章 评审办法", "1", "", "供应商合规性评审及企业信用",
                new BigDecimal("10.00"), "", "SUBJECTIVE", 13, "t1",
                List.of("c-1-1"), "供应商合规性评审及企业信用(10分）", 0.95,
                ChildAggregation.SUM, "scheme-1", "IN_SCHEME"
        );
        MultimodalScoreFact child1 = new MultimodalScoreFact(
                "第四章 评审办法", "1.1", "1", "ICP备案核查",
                new BigDecimal("5.00"), "1.进行 ICP 备案核查...得5分", "OBJECTIVE", 13, "t1",
                List.of("c-1-2"), "1.进行 ICP 备案核查", 0.95, ChildAggregation.SUM, "scheme-1", "IN_SCHEME"
        );
        MultimodalScoreFact child2 = new MultimodalScoreFact(
                "第四章 评审办法", "1.2", "1", "信用良好",
                new BigDecimal("5.00"), "2.投标人近三年信用良好...得5分", "OBJECTIVE", 13, "t1",
                List.of("c-1-2"), "2.投标人近三年信用良好", 0.95, ChildAggregation.SUM, "scheme-1", "IN_SCHEME"
        );

        List<MultimodalScoreFact> facts = List.of(parent, child1, child2);
        List<MultimodalScoreFact> recovered = CellSubRuleRecovery.resolve(facts, artifact);

        assertThat(recovered).hasSize(3);
    }

    @Test
    @DisplayName("端到端验证: MultimodalExtractionReviewPolicy.normalized 在面对漏抽细则时能完整补全并让叶子项闭合")
    void shouldNormalizeAndRecoverThroughExtractionPolicy() {
        CanonicalCellDto row1Name = new CanonicalCellDto("c-1-1", 1, 1, "供应商合规性评审及企业信用(10分）", "供应商合规性评审及企业信用", 1, 1, false, "pdf:page=13", 13, "r-1");
        CanonicalCellDto row1Rule = new CanonicalCellDto("c-1-2", 1, 2, CLAUSE_1_DETAIL, CLAUSE_1_DETAIL, 1, 1, false, "pdf:page=13", 13, "r-1");
        CanonicalCellDto row1Weight = new CanonicalCellDto("c-1-3", 1, 3, "10", "10", 1, 1, false, "pdf:page=13", 13, "r-1");
        CanonicalTable table = new CanonicalTable("t1", 13, "第四章 评审办法", 2, 4, List.of(),
                List.of(row1Name, row1Rule, row1Weight), true, "PDF");
        DocumentArtifact artifact = new DocumentArtifact(new ArtifactMeta("doc-1", "sha", "PDF", "2.2"), List.of(table), List.of());

        MultimodalScoreFact parent = new MultimodalScoreFact(
                "第四章 评审办法", "1", "", "供应商合规性评审及企业信用",
                new BigDecimal("10.00"), "", "SUBJECTIVE", 13, "t1",
                List.of("c-1-1"), "供应商合规性评审及企业信用(10分）", 0.95,
                ChildAggregation.SUM, "scheme-1", "IN_SCHEME"
        );
        MultimodalScoreFact child1 = new MultimodalScoreFact(
                "第四章 评审办法", "1.1", "1", "ICP备案核查",
                new BigDecimal("5.00"), "1.进行 ICP 备案核查...得5分", "OBJECTIVE", 13, "t1",
                List.of("c-1-2"), "1.进行 ICP 备案核查", 0.95, ChildAggregation.SUM, "scheme-1", "IN_SCHEME"
        );

        var rawResult = new MultimodalScoreFactExtractor.ExtractionResult(new BigDecimal("10.00"), List.of(parent, child1), "test", 0);
        String tenderText = "# 第四章 评审办法\n综合评分满分10分。\n";
        OriginalScoringContext context = OriginalScoringContext.resolve(artifact, tenderText);

        var normalized = MultimodalExtractionReviewPolicy.normalized(rawResult, context, artifact, tenderText);

        assertThat(normalized.facts()).hasSize(3);
        assertThat(normalized.facts()).anyMatch(f -> "1.2".equals(f.code()) && f.weight().compareTo(new BigDecimal("5.00")) == 0);

        var validator = new MultimodalScoreFactValidator();
        var report = validator.validate(normalized.facts(), artifact, context, tenderText, normalized.declaredTotal());
        assertThat(report.violations()).noneMatch(v -> "PARENT_WEIGHT_MISMATCH".equals(v.code()));
    }
}
