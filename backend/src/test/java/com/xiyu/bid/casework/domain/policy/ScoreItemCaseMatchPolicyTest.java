package com.xiyu.bid.casework.domain.policy;

import com.xiyu.bid.casework.domain.model.KnowledgeCaseReadModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
        @Override public Long getScoringPointId() { return id; }
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
    @DisplayName("编号与正文跨层匹配：评分项带层级编号2.2.4(3)，案例标题为技术方案但正文完整包含商城系统对接能力")
    void testNumberedScoreItemMatchingCandidateContent() {
        StubCandidate candidate = new StubCandidate(
                10L,
                "技术方案",
                "技术方案",
                "对供应商商城系统对接能力进行综合评价，供应商应根据工会规定定义的接口标准进行对接",
                "技术方案内容包含但不限于：商城系统对接能力供应商按照“三峡e购”商城定义的接口标准进行对接，依据接口标准向“三峡e购”商城提供接口服务，包括商品、商品组、订单、售后、对账等",
                "工业品",
                "央企",
                "WON"
        );

        ScoreItemCaseMatchPolicy.ScoreItemContext context = new ScoreItemCaseMatchPolicy.ScoreItemContext(
                201L,
                "2.2.4(3)",
                "2.2.4(3) 商城系统对接能力",
                "对供应商商城系统对接能力进行综合评价，供应商应根据工会规定定义的接口标准进行对接，使商城接口标准向职工商城采购接口服务，包括商品、商品组、订单、售后、对账等",
                "工业品",
                "央企",
                null
        );

        ScoreItemCaseMatchPolicy.MatchResult result = policy.evaluate(candidate, context);

        assertThat(result.caseId()).isEqualTo(10L);
        assertThat(result.score()).isGreaterThanOrEqualTo(80);
        assertThat(result.matchLabel()).isEqualTo("优质");
        assertThat(result.matchReasons()).contains("评分点匹配", "评分项条款高度契合", "类别一致");
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

    @ParameterizedTest
    @CsvSource({
            "'2.2.4(3) 商城系统对接能力', '商城系统对接能力'",
            "'第三章 3.2 (1) 应急保障方案', '应急保障方案'",
            "'【1】 双活容灾与数据备份', '双活容灾与数据备份'",
            "'A-1.2 企业资质与认证', '企业资质与认证'",
            "'一、 财务审计报告', '财务审计报告'",
            "'① 项目经理及核心团队配置', '项目经理及核心团队配置'",
            "'附表2-1 售后服务承诺与响应时间', '售后服务承诺与响应时间'",
            "'第2.1节 (2) 培训与技术支持', '培训与技术支持'",
            "'1-1-1 履约保函与保证金', '履约保函与保证金'",
            "'表3.2 业绩证明材料', '业绩证明材料'",
            "'纯业务名称无编号', '纯业务名称无编号'"
    })
    @DisplayName("编号清洗器全面测试：支持各种真实标书编号前缀剥离")
    void testCleanTitle_ComprehensivePrefixes(String rawTitle, String expectedClean) {
        String actual = ScoreItemCaseMatchPolicy.cleanTitle(rawTitle);
        assertThat(actual).isEqualTo(expectedClean);
    }

    @ParameterizedTest
    @CsvSource({
            "'第一章 (1) 财务审计报告与三年纳税证明', '商务要求', '商务', '我方提供经审计的财务报表及三年完税证明材料', '商务要求'",
            "'【资质】 A-1.2 ISO27001信息安全体系认证', '资质认证', '资质', '我方已取得ISO27001信息安全管理体系认证证书', '资质要求'",
            "'附表1-1 ① 项目经理及核心技术团队配置', '人员团队', '人员', '配置具备高级职称的项目经理及专业团队成员', '项目团队'"
    })
    @DisplayName("多类别覆盖测试：商务/资质/人员等不同类别与前缀均能精准识别召回")
    void testVariousCategoriesAndPrefixes(String itemDim, String caseCategory, String itemCategory, String caseResponse, String caseTitle) {
        StubCandidate candidate = new StubCandidate(
                50L,
                caseTitle,
                caseCategory,
                "招标文件相关要求说明",
                caseResponse,
                "综合",
                "央企",
                "WON"
        );

        ScoreItemCaseMatchPolicy.ScoreItemContext context = new ScoreItemCaseMatchPolicy.ScoreItemContext(
                301L,
                "CODE",
                itemDim,
                "详细条款说明：" + itemDim,
                "综合",
                "央企",
                null
        );

        ScoreItemCaseMatchPolicy.MatchResult result = policy.evaluate(candidate, context);

        assertThat(result.score()).isGreaterThanOrEqualTo(60);
        assertThat(result.matchLabel()).isIn("优质", "良好");
        assertThat(result.matchReasons()).contains("类别一致");
    }

    @Test
    @DisplayName("负向测试：完全不相关的评分项与案例不会产生高分误召回")
    void testNegativeCases_ShouldNotFalselyMatchHighScore() {
        StubCandidate technicalCase = new StubCandidate(
                99L,
                "技术方案",
                "技术方案",
                "微服务部署架构与接口开发",
                "提供微服务分布式架构及全链路压测方案",
                "综合",
                "民企",
                "LOST"
        );

        // 评分项为纯财务要求
        ScoreItemCaseMatchPolicy.ScoreItemContext financialContext = new ScoreItemCaseMatchPolicy.ScoreItemContext(
                401L,
                "3.1",
                "3.1 财务审计与资产负债率要求",
                "投标人须提供经会计师事务所审计的财务审计报告，资产负债率不高于70%",
                "综合",
                "国企",
                null
        );

        ScoreItemCaseMatchPolicy.MatchResult result = policy.evaluate(technicalCase, financialContext);

        assertThat(result.score()).isLessThan(40);
        assertThat(result.matchLabel()).isEqualTo("一般");
        assertThat(result.matchReasons()).doesNotContain("评分点匹配", "评分项条款高度契合", "类别一致");
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
