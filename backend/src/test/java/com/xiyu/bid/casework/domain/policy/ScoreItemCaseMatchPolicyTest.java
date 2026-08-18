package com.xiyu.bid.casework.domain.policy;

import com.xiyu.bid.casework.domain.model.KnowledgeCaseReadModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreItemCaseMatchPolicyTest {

    private final ScoreItemCaseMatchPolicy policy = new ScoreItemCaseMatchPolicy();

    private static class StubCandidate implements KnowledgeCaseReadModel {
        private final Long id;
        private final String scoringPointTitle;
        private final String scoringCategory;
        private final String requirementRaw;
        private final String responseText;
        private final String projectType;
        private final String customerType;
        private final String bidResult;

        StubCandidate(Long id, String scoringPointTitle, String scoringCategory,
                      String requirementRaw, String responseText,
                      String projectType, String customerType, String bidResult) {
            this.id = id;
            this.scoringPointTitle = scoringPointTitle;
            this.scoringCategory = scoringCategory;
            this.requirementRaw = requirementRaw;
            this.responseText = responseText;
            this.projectType = projectType;
            this.customerType = customerType;
            this.bidResult = bidResult;
        }

        @Override public Long getId() { return id; }
        @Override public Long getSourceProjectId() { return 100L; }
        @Override public String getSourceProjectName() { return "TestProject"; }
        @Override public String getScoringPointTitle() { return scoringPointTitle; }
        @Override public String getScoringCategory() { return scoringCategory; }
        @Override public String getRequirementRaw() { return requirementRaw; }
        @Override public String getResponseText() { return responseText; }
        @Override public Integer getReuseCount() { return 5; }
        @Override public Boolean getIsPinned() { return false; }
        @Override public String getStatus() { return "ACTIVE"; }
        @Override public String getCustomerType() { return customerType; }
        @Override public String getProjectType() { return projectType; }
        @Override public String getBidResult() { return bidResult; }
        @Override public String getProductLine() { return "IT"; }
        @Override public java.time.LocalDateTime getCreatedAt() { return java.time.LocalDateTime.now(); }
    }

    @Test
    @DisplayName("满分/高分匹配：条款匹配 + 类别一致 + 项目类型一致 + 客户类型一致 + 中标加成 + 关键词命中")
    void testHighScoreMatch() {
        StubCandidate candidate = new StubCandidate(
                1L,
                "系统架构高可用设计",
                "技术方案",
                "要求系统具备双活容灾与动态扩容能力，保障99.99%可用性",
                "采用同城双活与动态扩容架构，满足高可用要求",
                "IT_SERVICE",
                "STATE_OWNED",
                "WON"
        );

        ScoreItemCaseMatchPolicy.ScoreItemContext context = new ScoreItemCaseMatchPolicy.ScoreItemContext(
                101L,
                "A1",
                "技术方案",
                "系统架构高可用设计，支持双活容灾与动态扩容能力",
                "IT_SERVICE",
                "STATE_OWNED",
                "双活容灾"
        );

        ScoreItemCaseMatchPolicy.MatchResult result = policy.evaluate(candidate, context);

        assertThat(result.caseId()).isEqualTo(1L);
        assertThat(result.score()).isGreaterThanOrEqualTo(80);
        assertThat(result.matchLabel()).isEqualTo("优质");
        assertThat(result.matchReasons()).contains("评分项条款高度契合", "类别一致", "项目类型一致", "客户类型一致", "历史中标案例", "搜索词命中");
        assertThat(result.matchedKeywords()).contains("双活容灾");
    }

    @Test
    @DisplayName("部分匹配：仅类别一致与中标加成，detail不重合")
    void testPartialMatch() {
        StubCandidate candidate = new StubCandidate(
                2L,
                "财务审计报告",
                "商务要求",
                "提供近三年经审计的财务报表",
                "已提供2023-2025审计报告",
                "GOODS",
                "PRIVATE",
                "WON"
        );

        ScoreItemCaseMatchPolicy.ScoreItemContext context = new ScoreItemCaseMatchPolicy.ScoreItemContext(
                102L,
                "B1",
                "商务要求",
                "企业纳税信用等级须为A级",
                "IT_SERVICE",
                "STATE_OWNED",
                null
        );

        ScoreItemCaseMatchPolicy.MatchResult result = policy.evaluate(candidate, context);

        assertThat(result.caseId()).isEqualTo(2L);
        assertThat(result.score()).isLessThan(60);
        assertThat(result.matchLabel()).isEqualTo("一般");
        assertThat(result.matchReasons()).contains("类别一致", "历史中标案例");
    }

    @Test
    @DisplayName("边界测试：null 候选案例防御")
    void testNullCandidate() {
        ScoreItemCaseMatchPolicy.ScoreItemContext context = new ScoreItemCaseMatchPolicy.ScoreItemContext(
                103L, "C1", "其他", "详细描述", null, null, null
        );

        ScoreItemCaseMatchPolicy.MatchResult result = policy.evaluate(null, context);

        assertThat(result.score()).isEqualTo(0);
        assertThat(result.matchLabel()).isEqualTo("一般");
        assertThat(result.matchReasons()).isEmpty();
    }
}
