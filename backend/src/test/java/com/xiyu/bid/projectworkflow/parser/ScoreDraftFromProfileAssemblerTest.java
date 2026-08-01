package com.xiyu.bid.projectworkflow.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.biddraftagent.domain.ScoringCriterion;
import com.xiyu.bid.biddraftagent.domain.ScoringCriteriaSubType;
import com.xiyu.bid.projectworkflow.entity.ProjectScoreDraft;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreDraftFromProfileAssemblerTest {

    private final ScoreDraftFromProfileAssembler assembler = new ScoreDraftFromProfileAssembler(new ProjectScoreDraftMapper(new ObjectMapper()));

    @Test
    void assemble_ShouldConvertScoringCriteriaToDrafts() {
        List<ScoringCriterion> criteria = List.of(
                new ScoringCriterion("1", "技术方案", "整体架构设计", new BigDecimal("30"), ScoringCriteriaSubType.TECHNICAL_EVALUATION),
                new ScoringCriterion("2", "价格评分", "投标报价", new BigDecimal("40"), ScoringCriteriaSubType.PRICE_WEIGHT),
                new ScoringCriterion("3", "商务条款", "售后服务方案", new BigDecimal("10"), ScoringCriteriaSubType.SERVICE_EVALUATION)
        );

        var drafts = assembler.assemble(2001L, "招标文件.pdf", criteria);

        assertThat(drafts).hasSize(3);

        // 第一条：技术方案 -> category=technical
        ProjectScoreDraft d0 = drafts.get(0);
        assertThat(d0.getProjectId()).isEqualTo(2001L);
        assertThat(d0.getSourceFileName()).isEqualTo("招标文件.pdf");
        assertThat(d0.getScoreItemTitle()).isEqualTo("整体架构设计");
        assertThat(d0.getCategory()).isEqualTo("technical");
        assertThat(d0.getScoreValueText()).contains("30");
        assertThat(d0.getStatus()).isEqualTo(ProjectScoreDraft.Status.DRAFT);
        assertThat(d0.getSourceTableIndex()).isEqualTo(0);
        assertThat(d0.getSourceRowIndex()).isEqualTo(0);

        // 第二条：价格评分 -> category=price
        ProjectScoreDraft d1 = drafts.get(1);
        assertThat(d1.getCategory()).isEqualTo("price");
        assertThat(d1.getScoreItemTitle()).isEqualTo("投标报价");
        assertThat(d1.getSourceTableIndex()).isEqualTo(1);

        // 第三条：服务评价 -> category=business
        ProjectScoreDraft d2 = drafts.get(2);
        assertThat(d2.getCategory()).isEqualTo("business");
    }

    @Test
    void assemble_ShouldBuildScoreRuleTextFromDimensionAndIndicator() {
        List<ScoringCriterion> criteria = List.of(
                new ScoringCriterion("1", "技术方案", "整体架构设计", new BigDecimal("30"), ScoringCriteriaSubType.TECHNICAL_EVALUATION)
        );

        var drafts = assembler.assemble(1L, "test.pdf", criteria);

        assertThat(drafts.get(0).getScoreRuleText()).contains("技术方案");
        assertThat(drafts.get(0).getScoreRuleText()).contains("整体架构设计");
    }

    @Test
    void assemble_ShouldInferTaskActionAndGeneratedFields() {
        List<ScoringCriterion> criteria = List.of(
                new ScoringCriterion("1", "技术方案", "整体架构设计", new BigDecimal("30"), ScoringCriteriaSubType.TECHNICAL_EVALUATION)
        );

        var drafts = assembler.assemble(1L, "test.pdf", criteria);

        ProjectScoreDraft d = drafts.get(0);
        // ScoreDraftSeedFactory.inferTaskAction: "方案" -> "编写"
        assertThat(d.getTaskAction()).isEqualTo("编写");
        assertThat(d.getGeneratedTaskTitle()).contains("整体架构设计");
        assertThat(d.getGeneratedTaskDescription()).contains("整体架构设计");
        // suggestedDeliverables should be valid JSON array
        assertThat(d.getSuggestedDeliverables()).startsWith("[");
    }

    @Test
    void assemble_ShouldHandleNullWeight() {
        List<ScoringCriterion> criteria = List.of(
                new ScoringCriterion("1", "其他", "综合评价", null, ScoringCriteriaSubType.OTHER)
        );

        var drafts = assembler.assemble(1L, "test.pdf", criteria);

        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).getScoreValueText()).isNotNull();
    }

    @Test
    void assemble_ShouldReturnEmptyListForNullInput() {
        var drafts = assembler.assemble(1L, "test.pdf", null);
        assertThat(drafts).isEmpty();
    }

    @Test
    void assemble_ShouldReturnEmptyListForEmptyInput() {
        var drafts = assembler.assemble(1L, "test.pdf", List.of());
        assertThat(drafts).isEmpty();
    }

    @Test
    void assemble_ShouldSerializeDeliverablesAsJson() {
        List<ScoringCriterion> criteria = List.of(
                new ScoringCriterion("1", "技术方案", "整体架构设计", new BigDecimal("30"), ScoringCriteriaSubType.TECHNICAL_EVALUATION)
        );

        var drafts = assembler.assemble(1L, "test.pdf", criteria);

        // "方案" keyword -> deliverables include "方案正文"
        assertThat(drafts.get(0).getSuggestedDeliverables()).contains("方案正文");
    }
}
