package com.xiyu.bid.casework.domain.policy;

import com.xiyu.bid.casework.domain.model.KnowledgeCaseReadModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

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
        private String subTopic;
        private Integer qualityScore;

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

        StubCandidate(Long id, String scoringPointTitle, String scoringCategory,
                      String requirementRaw, String responseText,
                      String projectType, String customerType, String bidResult,
                      String subTopic, Integer qualityScore) {
            this(id, scoringPointTitle, scoringCategory, requirementRaw, responseText, projectType, customerType, bidResult);
            this.subTopic = subTopic;
            this.qualityScore = qualityScore;
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
        @Override public String getSubTopic() { return subTopic; }
        @Override public Integer getQualityScore() { return qualityScore; }
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
    @DisplayName("业绩评分项穿透资格项：评分项detail为空泛引用，穿透资格项业绩要求后高分召回优质业绩案例")
    void testPerformanceScoreItemWithQualificationRequirements() {
        StubCandidate candidate = new StubCandidate(
                99L,
                "同类项目业绩",
                "业绩",
                "供应商近三年需具有大型集团级企业电商化品类年度供应业绩，单项发票金额不低于2000万元",
                "我司自2023年起为大型集团级企业提供电商化采购年度供应服务，包含办公用品与标准工业品，单项年度发票累计金额达4500万元，合同履约评价优秀。",
                "工业品",
                "STATE_OWNED",
                "WON"
        );

        String qualText = "供应商近三年（2023年1月1日起至响应文件递交截止日），应至少具有1项大型集团级企业电商化品类年度供应业绩：参与标包1的，单项集团层级业绩的项目服务范围应包含办公用品且单个业绩所对应的合同项下发票在2023年1月1日起至响应文件递交截止日任意一个自然年内的累计金额不低于2000万元；参与标包2的，单项集团层级业绩的项目服务范围应包含标准工业品且单个业绩所对应的合同项下发票在2023年1月1日起至响应文件递交截止日任意一个自然年内的累计金额不低于4500万元。";
        List<String> qualKeywords = List.of("大型集团", "电商化", "办公用品", "标准工业品", "发票", "2000万元", "4500万元");

        ScoreItemCaseMatchPolicy.ScoreItemContext context = new ScoreItemCaseMatchPolicy.ScoreItemContext(
                301L,
                "3",
                "3 同类项目业绩",
                "在满足资格条件规定的2项有效业绩基础上...需提供的业绩证明材料同初步评审第7条供应商资格中的业绩证明材料",
                "工业品",
                "STATE_OWNED",
                null,
                "某大型央企采购项目",
                "某央企集团",
                qualText,
                qualKeywords
        );

        ScoreItemCaseMatchPolicy.MatchResult result = policy.evaluate(candidate, context);

        assertThat(result.caseId()).isEqualTo(99L);
        assertThat(result.score()).isGreaterThanOrEqualTo(80);
        assertThat(result.matchLabel()).isEqualTo("优质");
        assertThat(result.matchReasons()).anyMatch(r -> r.contains("资格项业绩品类"));
        assertThat(result.matchReasons()).anyMatch(r -> r.contains("资格项客户层级"));
        assertThat(result.matchReasons()).anyMatch(r -> r.contains("资格项门槛凭据"));
        assertThat(result.matchedKeywords()).contains("办公用品");
    }

    @Test
    @DisplayName("同属商务类别也不能把财务审计案例推荐给纳税信用要求")
    void testOffTopicCommercialCaseNotAdmitted() {
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
        assertThat(result.score()).isZero();
        assertThat(result.matchLabel()).isEqualTo("一般");
        assertThat(result.matchReasons()).containsExactly("与评分项内容不相关");
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
            "'纯业务名称无编号', '纯业务名称无编号'",
            "'ISO9001 质量管理体系认证', 'ISO9001 质量管理体系认证'",
            "'CMMI 软件成熟度认证', 'CMMI 软件成熟度认证'",
            "'B2B 电商交易平台', 'B2B 电商交易平台'",
            "'ERP 系统对接方案', 'ERP 系统对接方案'",
            "'AI 智能算法开发', 'AI 智能算法开发'"
    })
    @DisplayName("编号清洗器全面测试：支持各种真实标书编号前缀剥离并完整保留英文缩写与品牌名")
    void testCleanTitle_ComprehensivePrefixes(String rawTitle, String expectedClean) {
        String actual = ScoreItemCaseMatchPolicy.cleanTitle(rawTitle);
        assertThat(actual).isEqualTo(expectedClean);
    }

    @ParameterizedTest
    @CsvSource({
            "'第一章 (1) 财务审计报告与三年纳税证明', '商务要求', '我方提供经审计的财务报表及三年完税证明材料', '财务审计报告'",
            "'【资质】 A-1.2 ISO27001信息安全体系认证', '资质认证', '我方已取得ISO27001信息安全管理体系认证证书', '资质要求'",
            "'附表1-1 ① 项目经理及核心技术团队配置', '人员团队', '配置具备高级职称的项目经理及专业团队成员', '项目团队'"
    })
    @DisplayName("多类别覆盖测试：商务/资质/人员等不同类别与前缀均能精准识别召回")
    void testVariousCategoriesAndPrefixes(String itemDim, String caseCategory, String caseResponse, String caseTitle) {
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

    @Test
    @DisplayName("边界测试：null context 防御，避免 NPE")
    void testNullContext_DefensiveCheck() {
        StubCandidate candidate = new StubCandidate(
                99L, "技术方案", "技术方案", "需求", "响应", "IT", "央企", "WON"
        );
        ScoreItemCaseMatchPolicy.MatchResult result = policy.evaluate(candidate, null);
        assertThat(result.caseId()).isEqualTo(99L);
        assertThat(result.score()).isEqualTo(0);
        assertThat(result.matchLabel()).isEqualTo("一般");
        assertThat(result.matchReasons()).isEmpty();

        ScoreItemCaseMatchPolicy.MatchResult nullBoth = policy.evaluate(null, null);
        assertThat(nullBoth.caseId()).isNull();
        assertThat(nullBoth.score()).isEqualTo(0);
    }

    @Test
    @DisplayName("实体加权断层匹配：三峡项目优先强匹配三峡历史案例，新疆中泰案例得分显著偏低")
    void testProjectEntityMatching_ThreeGorgesProjectOutranksZhongtai() {
        StubCandidate threeGorgesCase = new StubCandidate(
                1L,
                "三峡e购电商平台对接及运维方案",
                "技术方案",
                "对投标人的系统技术方案与三峡e购系统对接、整体部署架构、安全保障措施进行综合评审。",
                "针对三峡集团需求，我司已完成与三峡e购商城的深度系统对接，提供标准RESTful API接口，涵盖商品组推送、实时库存查询、三峡集团各电站统一电子发票开具及月度自动化对账方案。",
                "工业品",
                "央企",
                "WON"
        );

        StubCandidate zhongtaiCase = new StubCandidate(
                2L,
                "技术方案与系统实施保障",
                "技术方案",
                "对投标人的系统技术方案、整体部署架构、安全保障措施、售后服务与应急响应方案进行综合评审，要求具备完善的高可用架构与运维服务团队。",
                "针对中泰化学工业品采购需求，我司提供完备的技术方案与系统实施保障体系。系统采用云原生微服务高可用部署架构，保障99.99%可用性；设立专职安全保障团队与7x24小时运维服务中心。",
                "工业品",
                "国企",
                "WON"
        );

        // 模拟项目 1358（三峡项目）上下文
        ScoreItemCaseMatchPolicy.ScoreItemContext threeGorgesProjectContext = new ScoreItemCaseMatchPolicy.ScoreItemContext(
                1001L,
                "1.1",
                "技术方案与实施保障",
                "对投标人的系统技术方案、整体部署架构、安全保障措施、售后服务与应急响应方案进行综合评审。",
                "工业品",
                "央企",
                null,
                "中国长江三峡集团2026年办公及工业品电商化采购项目",
                "中国长江三峡集团有限公司"
        );

        ScoreItemCaseMatchPolicy.MatchResult threeGorgesResult = policy.evaluate(threeGorgesCase, threeGorgesProjectContext);
        ScoreItemCaseMatchPolicy.MatchResult zhongtaiResult = policy.evaluate(zhongtaiCase, threeGorgesProjectContext);

        // 三峡案例命中“三峡”核心实体，大幅领先中泰案例
        assertThat(threeGorgesResult.score()).isGreaterThan(zhongtaiResult.score());
        assertThat(threeGorgesResult.matchReasons()).anyMatch(r -> r.contains("命中项目专属实体"));
        assertThat(threeGorgesResult.matchedKeywords()).contains("三峡");
        assertThat(zhongtaiResult.matchReasons()).noneMatch(r -> r.contains("命中项目专属实体"));
    }

    @Test
    @DisplayName("实体分词器测试：正确提取专有名词并过滤通用后缀与停用词")
    void testExtractEntityTokens() {
        var entities = TextSimilarityPolicy.extractEntityTokens(
                "中国长江三峡集团2026年办公及工业品电商化采购项目",
                "中国长江三峡集团有限公司"
        );

        assertThat(entities).contains("三峡");
        assertThat(entities).doesNotContain("有限公司", "集团", "采购项目", "2026年", "项目");
    }

    @Test
    @DisplayName("大类冲突一票否决：技术评分项 vs 商务财务案例，一票否决为0分且客户/项目加分绝不穿透")
    void testCategoryHardConflict_TechnicalItemVsCommercialCase_VetoedWithZeroScore() {
        // 候选案例为商务类（财务审计），但携带三峡项目名称与中标状态
        StubCandidate commercialCaseWithBonus = new StubCandidate(
                88L,
                "财务审计报告与资产负债率说明",
                "商务要求",
                "投标人须提供经会计师事务所审计的财务审计报告，资产负债率不高于70%",
                "三峡集团项目历史财务审计证明，经审计资产负债率为55%，完税证明齐全",
                "工业品",
                "央企",
                "WON"
        );

        // 评分项为纯技术方案
        ScoreItemCaseMatchPolicy.ScoreItemContext technicalContext = new ScoreItemCaseMatchPolicy.ScoreItemContext(
                501L,
                "1.1",
                "技术方案与实施保障",
                "对投标人的系统技术方案、整体部署架构、安全保障措施进行综合评审",
                "工业品",
                "央企",
                null,
                "中国长江三峡集团2026年办公及工业品电商化采购项目",
                "中国长江三峡集团有限公司"
        );

        ScoreItemCaseMatchPolicy.MatchResult result = policy.evaluate(commercialCaseWithBonus, technicalContext);

        // 即使客户类型一致(+5)、项目类型一致(+10)、中标案例(+5)、三峡实体命中(+25)，也必须被一票否决，得分为0！
        assertThat(result.score()).isEqualTo(0);
        assertThat(result.matchLabel()).isEqualTo("一般");
        assertThat(result.matchReasons()).contains("大类冲突一票否决");
        assertThat(result.matchedKeywords()).isEmpty();
    }

    @Test
    @DisplayName("大类冲突一票否决：商务财务评分项 vs 技术方案案例，一票否决为0分")
    void testCategoryHardConflict_CommercialItemVsTechnicalCase_VetoedWithZeroScore() {
        StubCandidate technicalCase = new StubCandidate(
                89L,
                "微服务部署架构与接口开发方案",
                "技术方案",
                "提供高可用微服务架构",
                "微服务部署架构满足高可用要求",
                "工业品",
                "央企",
                "WON"
        );

        ScoreItemCaseMatchPolicy.ScoreItemContext commercialContext = new ScoreItemCaseMatchPolicy.ScoreItemContext(
                502L,
                "3.1",
                "3.1 财务审计与资产负债率要求",
                "投标人须提供经会计师事务所审计的财务审计报告",
                "工业品",
                "央企",
                null
        );

        ScoreItemCaseMatchPolicy.MatchResult result = policy.evaluate(technicalCase, commercialContext);

        assertThat(result.score()).isEqualTo(0);
        assertThat(result.matchLabel()).isEqualTo("一般");
        assertThat(result.matchReasons()).contains("大类冲突一票否决");
        assertThat(result.matchedKeywords()).isEmpty();
    }

    @Test
    @DisplayName("大类兼容性测试：技术方案评分项 vs 资质认证案例，大类兼容不否决")
    void testCategoryCompatibility_TechnicalItemVsQualificationCase_NotVetoed() {
        StubCandidate qualificationCase = new StubCandidate(
                90L,
                "ISO27001信息安全管理体系认证证书",
                "资质认证",
                "提供ISO27001认证体系",
                "已获得信息安全管理体系认证证书",
                "工业品",
                "央企",
                "WON"
        );

        ScoreItemCaseMatchPolicy.ScoreItemContext technicalContext = new ScoreItemCaseMatchPolicy.ScoreItemContext(
                503L,
                "2.1",
                "系统架构高可用设计",
                "系统架构高可用设计，支持双活容灾与动态扩容能力",
                "工业品",
                "央企",
                null
        );

        ScoreItemCaseMatchPolicy.MatchResult result = policy.evaluate(qualificationCase, technicalContext);

        assertThat(result.matchReasons()).doesNotContain("大类冲突一票否决");
    }

    @Test
    @DisplayName("大类兼容性测试：商务同类业绩评分项 vs 技术方案案例，商务业绩与技术兼容不否决")
    void testCategoryCompatibility_PerformanceItemVsTechnicalCase_NotVetoed() {
        StubCandidate technicalCase = new StubCandidate(
                92L,
                "微服务架构高可用实施方案",
                "技术方案",
                "微服务部署架构满足高可用要求",
                "我司具备大型微服务实施案例与同类电商平台建设经验，提供微服务高可用方案",
                "工业品",
                "央企",
                "WON"
        );

        ScoreItemCaseMatchPolicy.ScoreItemContext performanceContext = new ScoreItemCaseMatchPolicy.ScoreItemContext(
                508L,
                "2.1",
                "商务-同类业绩",
                "投标人须提供近三年承担过同类信息化项目的有效合同业绩",
                "工业品",
                "央企",
                null
        );

        ScoreItemCaseMatchPolicy.MatchResult result = policy.evaluate(technicalCase, performanceContext);

        assertThat(result.score()).isGreaterThan(0);
        assertThat(result.matchReasons()).doesNotContain("大类冲突一票否决");
    }

    @Test
    @DisplayName("类别分类与兼容：技术项包含实施案例、配置方案、在线支付、等级保护不被误判与否决")
    void testTechnicalItems_WithCaseConfigPayment_NotMisclassifiedNorVetoed() {
        assertThat(CaseCategoryMatchPolicy.classifyText("微服务实施案例"))
                .isEqualTo(CaseCategoryMatchPolicy.MacroCategory.TECHNICAL);
        assertThat(CaseCategoryMatchPolicy.classifyText("双活容灾配置方案"))
                .isEqualTo(CaseCategoryMatchPolicy.MacroCategory.TECHNICAL);
        assertThat(CaseCategoryMatchPolicy.classifyText("在线支付对接"))
                .isEqualTo(CaseCategoryMatchPolicy.MacroCategory.TECHNICAL);
        assertThat(CaseCategoryMatchPolicy.classifyText("网络安全等级保护"))
                .isEqualTo(CaseCategoryMatchPolicy.MacroCategory.TECHNICAL);

        StubCandidate technicalCase = new StubCandidate(
                95L,
                "微服务实施案例与接口开发方案",
                "技术方案",
                "微服务实施案例对接要求",
                "成功完成微服务实施案例与在线支付对接，提供双活容灾配置方案及网络安全等级保护",
                "工业品",
                "央企",
                "WON"
        );
        ScoreItemCaseMatchPolicy.ScoreItemContext technicalContext = new ScoreItemCaseMatchPolicy.ScoreItemContext(
                505L,
                "2.1",
                "双活容灾配置方案与在线支付对接",
                "要求提供双活容灾配置方案，支持在线支付对接与微服务实施案例",
                "工业品",
                "央企",
                "在线支付"
        );

        ScoreItemCaseMatchPolicy.MatchResult result = policy.evaluate(technicalCase, technicalContext);
        assertThat(result.score()).isGreaterThanOrEqualTo(60);
        assertThat(result.matchReasons()).doesNotContain("大类冲突一票否决");
        assertThat(result.matchReasons()).contains("类别一致");
    }

    @Test
    @DisplayName("US5 向量语义通道：当评分项与案例无公共字词但具备高向量余弦相似度时，突破一票否决门禁被召回")
    void testVectorSemanticSimilarity_BreaksThroughZeroLexicalOverlap() {
        StubCandidate candidate = new StubCandidate(
                999L,
                "冷链恒温监控仓储",
                "技术方案",
                "冷链恒温监控仓储说明",
                "温湿度超限报警与喷淋降温",
                "工业品",
                "央企",
                "WON"
        );

        // 字面无交集无子主题匹配
        ScoreItemCaseMatchPolicy.ScoreItemContext contextWithoutVector = new ScoreItemCaseMatchPolicy.ScoreItemContext(
                999L,
                "3.1",
                "智能语音交互",
                "语义理解与多轮会话应答",
                "工业品",
                "央企",
                null
        );

        // 无向量相似度时，字面完全无关应得 0 分
        ScoreItemCaseMatchPolicy.MatchResult resultLexical = policy.evaluate(candidate, contextWithoutVector);
        assertThat(resultLexical.score()).isEqualTo(0);

        // 注入向量余弦相似度 0.82
        ScoreItemCaseMatchPolicy.ScoreItemContext contextWithVector = new ScoreItemCaseMatchPolicy.ScoreItemContext(
                999L,
                "3.1",
                "智能语音交互",
                "语义理解与多轮会话应答",
                "工业品",
                "央企",
                null,
                null,
                null,
                null,
                java.util.List.of(),
                java.util.Map.of(999L, 0.82)
        );

        ScoreItemCaseMatchPolicy.MatchResult resultVector = policy.evaluate(candidate, contextWithVector);
        assertThat(resultVector.score()).isGreaterThanOrEqualTo(30);
        assertThat(resultVector.matchReasons()).anyMatch(r -> r.contains("语义相似度匹配") && r.contains("82%"));
    }

    @Test
    @DisplayName("US5 向量优雅降级：向量集合为 null 或为空时，安全回退到纯词法评估，零异常")
    void testVectorSemanticSimilarity_GracefullyDegradesWhenVectorAbsent() {
        StubCandidate candidate = new StubCandidate(
                1000L,
                "系统架构高可用设计",
                "技术方案",
                "系统架构高可用设计要求",
                "提供双活容灾与动态扩容能力",
                "工业品",
                "央企",
                "WON"
        );

        ScoreItemCaseMatchPolicy.ScoreItemContext contextWithNullVectors = new ScoreItemCaseMatchPolicy.ScoreItemContext(
                1000L,
                "1.1",
                "高可用设计",
                "双活容灾方案",
                "工业品",
                "央企",
                null,
                null,
                null,
                null,
                java.util.List.of(),
                null
        );

        ScoreItemCaseMatchPolicy.MatchResult result = policy.evaluate(candidate, contextWithNullVectors);
        assertThat(result).isNotNull();
        assertThat(result.score()).isGreaterThan(0);
    }

    @Test
    @DisplayName("顿号分层解析与展示关键词优先级：纯净标题与顿号实体优先入选，绝无碎片切词污染")
    void testPrioritizedKeywords_CleanTitleAndPunctuationEntitiesWithoutFragments() {
        StubCandidate candidate = new StubCandidate(
                91L,
                "技术方案",
                "技术方案",
                "商城系统对接能力综合评价",
                "技术方案内容：商城系统对接能力全面满足，包括商品、商品组、订单、售后、对账等，具备双活容灾标准化能力",
                "工业品",
                "央企",
                "WON"
        );

        ScoreItemCaseMatchPolicy.ScoreItemContext context = new ScoreItemCaseMatchPolicy.ScoreItemContext(
                504L,
                "2.2.4(3)",
                "2.2.4(3) 商城系统对接能力",
                "对供应商商城系统对接能力进行综合评价，向职工商城采购接口服务，包括商品、商品组、订单、售后、对账等，具备双活容灾标准化能力",
                "工业品",
                "央企",
                null
        );

        ScoreItemCaseMatchPolicy.MatchResult result = policy.evaluate(candidate, context);

        assertThat(result.score()).isGreaterThanOrEqualTo(80);
        // 核心纯净标题与顿号提取的高质量实体优先展示
        assertThat(result.matchedKeywords()).contains("商城系统对接能力", "商品", "商品组", "订单", "售后", "对账");
        // 绝不包含任何滑动切词碎末
        assertThat(result.matchedKeywords()).noneMatch(kw ->
                kw.equals("双的") || kw.equals("的标") || kw.equals("可编") || kw.startsWith("的") || kw.endsWith("的")
        );
    }

    @Test
    @DisplayName("大类策略纯核心单测：互斥判定仅限纯技术与纯商务，其他相互兼容，OTHER类别安全兼容")
    void testCaseCategoryMatchPolicy_MutuallyExclusive() {
        assertThat(CaseCategoryMatchPolicy.isMutuallyExclusive(
                CaseCategoryMatchPolicy.MacroCategory.TECHNICAL,
                CaseCategoryMatchPolicy.MacroCategory.COMMERCIAL)).isTrue();
        assertThat(CaseCategoryMatchPolicy.isMutuallyExclusive(
                CaseCategoryMatchPolicy.MacroCategory.COMMERCIAL,
                CaseCategoryMatchPolicy.MacroCategory.TECHNICAL)).isTrue();
        assertThat(CaseCategoryMatchPolicy.isMutuallyExclusive(
                CaseCategoryMatchPolicy.MacroCategory.TECHNICAL,
                CaseCategoryMatchPolicy.MacroCategory.PERFORMANCE)).isFalse();
        assertThat(CaseCategoryMatchPolicy.isMutuallyExclusive(
                CaseCategoryMatchPolicy.MacroCategory.PERFORMANCE,
                CaseCategoryMatchPolicy.MacroCategory.TECHNICAL)).isFalse();
        assertThat(CaseCategoryMatchPolicy.isMutuallyExclusive(
                CaseCategoryMatchPolicy.MacroCategory.PERFORMANCE,
                CaseCategoryMatchPolicy.MacroCategory.COMMERCIAL)).isFalse();
        assertThat(CaseCategoryMatchPolicy.isMutuallyExclusive(
                CaseCategoryMatchPolicy.MacroCategory.TECHNICAL,
                CaseCategoryMatchPolicy.MacroCategory.TECHNICAL)).isFalse();
        assertThat(CaseCategoryMatchPolicy.isMutuallyExclusive(
                CaseCategoryMatchPolicy.MacroCategory.TECHNICAL,
                CaseCategoryMatchPolicy.MacroCategory.QUALIFICATION)).isFalse();
        assertThat(CaseCategoryMatchPolicy.isMutuallyExclusive(
                CaseCategoryMatchPolicy.MacroCategory.QUALIFICATION,
                CaseCategoryMatchPolicy.MacroCategory.COMMERCIAL)).isFalse();
        assertThat(CaseCategoryMatchPolicy.isMutuallyExclusive(
                CaseCategoryMatchPolicy.MacroCategory.PERSONNEL,
                CaseCategoryMatchPolicy.MacroCategory.COMMERCIAL)).isFalse();
        assertThat(CaseCategoryMatchPolicy.isMutuallyExclusive(
                CaseCategoryMatchPolicy.MacroCategory.PERSONNEL,
                CaseCategoryMatchPolicy.MacroCategory.TECHNICAL)).isFalse();
        assertThat(CaseCategoryMatchPolicy.isMutuallyExclusive(
                CaseCategoryMatchPolicy.MacroCategory.OTHER,
                CaseCategoryMatchPolicy.MacroCategory.TECHNICAL)).isFalse();
        assertThat(CaseCategoryMatchPolicy.isMutuallyExclusive(null, null)).isFalse();
    }

    @Test
    @DisplayName("大类策略纯核心单测：维度/详情分层分类与精炼关键词解析")
    void testCaseCategoryMatchPolicy_Classification() {
        assertThat(CaseCategoryMatchPolicy.classifyText("")).isEqualTo(CaseCategoryMatchPolicy.MacroCategory.OTHER);
        assertThat(CaseCategoryMatchPolicy.classifyText(null)).isEqualTo(CaseCategoryMatchPolicy.MacroCategory.OTHER);
        assertThat(CaseCategoryMatchPolicy.classifyText("项目经理及人员配置")).isEqualTo(CaseCategoryMatchPolicy.MacroCategory.PERSONNEL);
        assertThat(CaseCategoryMatchPolicy.classifyText("项目经理及团队投入")).isEqualTo(CaseCategoryMatchPolicy.MacroCategory.PERSONNEL);
        assertThat(CaseCategoryMatchPolicy.classifyText("分支机构与专业技术服务")).isEqualTo(CaseCategoryMatchPolicy.MacroCategory.PERSONNEL);
        assertThat(CaseCategoryMatchPolicy.classifyText("ISO27001认证体系及资质等级证书")).isEqualTo(CaseCategoryMatchPolicy.MacroCategory.QUALIFICATION);
        assertThat(CaseCategoryMatchPolicy.classifyText("三年财务审计报告与报价")).isEqualTo(CaseCategoryMatchPolicy.MacroCategory.COMMERCIAL);
        assertThat(CaseCategoryMatchPolicy.classifyText("货款支付与结算条款")).isEqualTo(CaseCategoryMatchPolicy.MacroCategory.COMMERCIAL);
        assertThat(CaseCategoryMatchPolicy.classifyText("近三年同类项目业绩")).isEqualTo(CaseCategoryMatchPolicy.MacroCategory.PERFORMANCE);
        assertThat(CaseCategoryMatchPolicy.classifyText("同类业绩证明材料")).isEqualTo(CaseCategoryMatchPolicy.MacroCategory.PERFORMANCE);
        assertThat(CaseCategoryMatchPolicy.classifyText("商务-同类业绩")).isEqualTo(CaseCategoryMatchPolicy.MacroCategory.PERFORMANCE);
        // 混合类别「资质业绩」必须按业绩优先归类（否则真业绩案例丢类别一致加分）
        assertThat(CaseCategoryMatchPolicy.classifyText("资质业绩")).isEqualTo(CaseCategoryMatchPolicy.MacroCategory.PERFORMANCE);
        assertThat(CaseCategoryMatchPolicy.classifyText("资质等级证书")).isEqualTo(CaseCategoryMatchPolicy.MacroCategory.QUALIFICATION);
        // 纯资质文本含“同类”时不得翻转为业绩
        assertThat(CaseCategoryMatchPolicy.classifyText("同类资质认证")).isEqualTo(CaseCategoryMatchPolicy.MacroCategory.QUALIFICATION);
        assertThat(CaseCategoryMatchPolicy.classifyText("同类ISO认证")).isEqualTo(CaseCategoryMatchPolicy.MacroCategory.QUALIFICATION);
        assertThat(CaseCategoryMatchPolicy.classifyText("同类项目实施经验")).isEqualTo(CaseCategoryMatchPolicy.MacroCategory.PERFORMANCE);
        assertThat(CaseCategoryMatchPolicy.classifyText("微服务架构高可用实施方案")).isEqualTo(CaseCategoryMatchPolicy.MacroCategory.TECHNICAL);
        assertThat(CaseCategoryMatchPolicy.classifyText("微服务实施案例")).isEqualTo(CaseCategoryMatchPolicy.MacroCategory.TECHNICAL);
        assertThat(CaseCategoryMatchPolicy.classifyText("双活容灾配置方案")).isEqualTo(CaseCategoryMatchPolicy.MacroCategory.TECHNICAL);
        assertThat(CaseCategoryMatchPolicy.classifyText("在线支付对接")).isEqualTo(CaseCategoryMatchPolicy.MacroCategory.TECHNICAL);
        assertThat(CaseCategoryMatchPolicy.classifyText("网络安全等级保护")).isEqualTo(CaseCategoryMatchPolicy.MacroCategory.TECHNICAL);

        // 标题为OTHER时，从详情中推断大类
        assertThat(CaseCategoryMatchPolicy.classifyMacroCategory("条款1.1", "要求提供项目经理及人员配置"))
                .isEqualTo(CaseCategoryMatchPolicy.MacroCategory.PERSONNEL);
        // 标题为泛化商务时，从详情中精准推断为业绩大类
        assertThat(CaseCategoryMatchPolicy.classifyMacroCategory("商务要求", "近三年同类项目业绩合同"))
                .isEqualTo(CaseCategoryMatchPolicy.MacroCategory.PERFORMANCE);
    }

    @Test
    @DisplayName("大类策略纯核心单测：null或空白dim绝不能因为空串包含而误得满分")
    void testCalculateCategoryScore_DefensiveAgainstNullAndBlankDim() {
        // 当 dim 为 null 或空白，且 detail 为空或不匹配时，绝不能返回 15 分
        assertThat(CaseCategoryMatchPolicy.calculateCategoryScore(null, null, "技术方案")).isEqualTo(0);
        assertThat(CaseCategoryMatchPolicy.calculateCategoryScore("", "", "技术方案")).isEqualTo(0);
        assertThat(CaseCategoryMatchPolicy.calculateCategoryScore("   ", "不相关文本", "技术方案")).isEqualTo(0);

        // 当 dim 为 null，但 detail 明确为技术方案时，应根据 detail 识别大类得分
        assertThat(CaseCategoryMatchPolicy.calculateCategoryScore(null, "系统技术架构与微服务部署方案", "技术方案"))
                .isEqualTo(CaseCategoryMatchPolicy.CATEGORY_MATCH_SCORE);

        // 当 candidateCategory 为 null 或空时，得分为 0
        assertThat(CaseCategoryMatchPolicy.calculateCategoryScore("技术方案", "系统设计", null)).isEqualTo(0);
        assertThat(CaseCategoryMatchPolicy.calculateCategoryScore("技术方案", "系统设计", "")).isEqualTo(0);

        // 正常匹配场景
        assertThat(CaseCategoryMatchPolicy.calculateCategoryScore("技术方案", "系统架构", "技术方案"))
                .isEqualTo(CaseCategoryMatchPolicy.CATEGORY_MATCH_SCORE);
    }

    @Test
    @DisplayName("关键词提取优先级与8个上限截断测试：专属实体 > 维度名称 > 详情顿号实体 > 搜索词，最多严格保留8个")
    void testAssemblePrioritizedKeywords_MaxKeywordsCountLimit() {
        StubCandidate candidate = new StubCandidate(
                99L,
                "技术方案",
                "技术方案",
                "项目三峡与华为联合研发，包含微服务架构、高可用设计、双活容灾、动态扩容、在线支付、应急响应、需求分析等核心能力，性能优异",
                "三峡与华为技术方案交付",
                "IT",
                "央企",
                "WON"
        );

        ScoreItemCaseMatchPolicy.ScoreItemContext context = new ScoreItemCaseMatchPolicy.ScoreItemContext(
                200L,
                "B1",
                "技术方案",
                "包括微服务架构、高可用设计、双活容灾、动态扩容、在线支付、应急响应、需求分析等",
                "IT",
                "央企",
                "性能",
                "三峡项目",
                "华为公司"
        );

        ScoreItemCaseMatchPolicy.MatchResult result = policy.evaluate(candidate, context);

        // 验证关键词严格截断为 8 个
        assertThat(result.matchedKeywords()).hasSize(8);
        // 项目专属实体优先
        assertThat(result.matchedKeywords().get(0)).isIn("三峡", "华为");
        assertThat(result.matchedKeywords().get(1)).isIn("三峡", "华为");
        // 维度优先
        assertThat(result.matchedKeywords().get(2)).isEqualTo("技术方案");
        // 详情标点实体按顺序填满
        assertThat(result.matchedKeywords().subList(3, 8))
                .containsExactly("微服务架构", "高可用设计", "双活容灾", "动态扩容", "在线支付");
        // 超出 8 个的详情实体与后续搜索词（应急响应、需求分析、性能）被截断
        assertThat(result.matchedKeywords()).doesNotContain("应急响应", "需求分析", "性能");
    }

    @Test
    @DisplayName("防御性测试：Candidate与Context字段全为null时安全返回，不抛出NPE")
    void testEvaluate_NullFieldsDefensive() {
        StubCandidate candidate = new StubCandidate(null, null, null, null, null, null, null, null);
        ScoreItemCaseMatchPolicy.ScoreItemContext context = new ScoreItemCaseMatchPolicy.ScoreItemContext(
                null, null, null, null, null, null, null, null, null
        );

        ScoreItemCaseMatchPolicy.MatchResult result = policy.evaluate(candidate, context);

        assertThat(result).isNotNull();
        assertThat(result.score()).isEqualTo(0);
        assertThat(result.matchLabel()).isEqualTo("一般");
        assertThat(result.matchReasons()).isEmpty();
        assertThat(result.matchedKeywords()).isEmpty();
    }

    @Test
    @DisplayName("防回归测试：真实项目名称“朱双双的标讯不可编辑”，严禁提取双字滑窗碎片进入匹配原因与展示词")
    void testProjectEntityMatch_NoTwoGramFragmentsInReasonsOrKeywords() {
        StubCandidate candidateWithProjectName = new StubCandidate(
                999L,
                "技术方案与实施保障",
                "技术要求",
                "投标人应具备系统设计与实施保障方案，满足技术要求。",
                "朱双双的历史投标响应案例详情，包含技术架构与交付方案",
                "工业品",
                "央企",
                "WON"
        );

        ScoreItemCaseMatchPolicy.ScoreItemContext context = new ScoreItemCaseMatchPolicy.ScoreItemContext(
                1358L,
                "1.1",
                "技术方案与实施保障",
                "对投标人的系统技术方案、整体部署架构进行综合评审",
                "工业品",
                "央企",
                null,
                "朱双双的标讯不可编辑",
                null
        );

        ScoreItemCaseMatchPolicy.MatchResult result = policy.evaluate(candidateWithProjectName, context);

        // 绝不能在匹配原因中出现类似“命中项目专属实体(的标、不可)”等荒谬碎片
        assertThat(result.matchReasons()).noneMatch(r -> r.contains("的标") || r.contains("不可") || r.contains("双的") || r.contains("讯不"));

        // 关键词展示中，绝不能包含 2-gram 机械碎片或超长整句
        assertThat(result.matchedKeywords()).doesNotContain(
                "朱双", "双双", "双的", "的标", "标讯", "讯不", "不可", "编辑", "不可编辑", "朱双双的标讯不可编辑"
        );
        // 如果命中实体，应当是清晰干净的“朱双双”
        if (result.matchReasons().stream().anyMatch(r -> r.contains("命中项目专属实体"))) {
            assertThat(result.matchReasons()).anyMatch(r -> r.contains("朱双双"));
            assertThat(result.matchedKeywords()).contains("朱双双");
        }
    }

    @Test
    @DisplayName("业务子主题精确匹配断层优先：同等条件下，子主题精准对齐案例大幅领先")
    void testSubTopicExactMatchOutranksOtherTopic() {
        // 候选案例A：子主题为系统对接与数据集成（INTEGRATION）
        StubCandidate caseIntegration = new StubCandidate(
                1001L, "技术对接规范", "技术方案",
                "商城接口与ERP系统规范要求", "提供完整的接口调用机制与系统集成能力",
                "IT", "央企", "WON", "INTEGRATION", 75
        );
        // 候选案例B：子主题为售后服务（SLA_SERVICE）
        StubCandidate caseSla = new StubCandidate(
                1002L, "技术对接规范", "技术方案",
                "商城接口与ERP系统规范要求", "提供完整的接口调用机制与系统集成能力",
                "IT", "央企", "WON", "SLA_SERVICE", 75
        );

        ScoreItemCaseMatchPolicy.ScoreItemContext context = new ScoreItemCaseMatchPolicy.ScoreItemContext(
                888L, "T-1", "系统对接与数据集成方案",
                "要求投标人制定详尽的ERP对接与接口联调实施方案",
                "IT", "央企", null, null, null
        );

        ScoreItemCaseMatchPolicy.MatchResult resultA = policy.evaluate(caseIntegration, context);
        ScoreItemCaseMatchPolicy.MatchResult resultB = policy.evaluate(caseSla, context);

        assertThat(resultA.score()).isGreaterThan(resultB.score());
        assertThat(resultA.score() - resultB.score()).isEqualTo(CaseSubTopicPolicy.SUB_TOPIC_EXACT_MATCH_SCORE);
        assertThat(resultA.matchReasons()).contains("同类业务子主题(系统对接与数据集成)");
    }

    @Test
    @DisplayName("清洗质量分加成：质量分>=80获得额外加分")
    void testQualityScoreBonus() {
        StubCandidate highQualityCase = new StubCandidate(
                1003L, "技术方案", "技术方案",
                "技术规范要求说明", "技术响应详细正文",
                "IT", "央企", "WON", "OTHER", 90
        );
        StubCandidate lowQualityCase = new StubCandidate(
                1004L, "技术方案", "技术方案",
                "技术规范要求说明", "技术响应详细正文",
                "IT", "央企", "WON", "OTHER", 50
        );

        ScoreItemCaseMatchPolicy.ScoreItemContext context = new ScoreItemCaseMatchPolicy.ScoreItemContext(
                889L, "T-2", "技术方案", "技术规范要求说明",
                "IT", "央企", null, null, null
        );

        ScoreItemCaseMatchPolicy.MatchResult resHigh = policy.evaluate(highQualityCase, context);
        ScoreItemCaseMatchPolicy.MatchResult resLow = policy.evaluate(lowQualityCase, context);

        assertThat(resHigh.score() - resLow.score()).isEqualTo(5);
    }

    @Test
    @DisplayName("项目专属核心实体断层优先：命中客户核心实体（如三峡）获得专属加成")
    void testExclusiveClientEntityBoost() {
        StubCandidate threeGorgesCase = new StubCandidate(
                1005L, "电商化采购平台技术实施方案", "技术方案",
                "三峡集团电商采购对接要求", "中国长江三峡集团专用电商化采购技术规范应答正文",
                "IT", "央企", "WON", "INTEGRATION", 85
        );
        StubCandidate genericCase = new StubCandidate(
                1006L, "电商化采购平台技术实施方案", "技术方案",
                "通用电商采购对接要求", "通用企业电商化采购技术规范应答正文",
                "IT", "央企", "WON", "INTEGRATION", 85
        );

        ScoreItemCaseMatchPolicy.ScoreItemContext context = new ScoreItemCaseMatchPolicy.ScoreItemContext(
                890L, "T-3", "电商化采购平台技术实施方案",
                "满足三峡集团电商采购接口与服务要求",
                "IT", "央企", null,
                "中国长江三峡集团2026年办公及工业品采购项目", "三峡集团"
        );

        ScoreItemCaseMatchPolicy.MatchResult resTg = policy.evaluate(threeGorgesCase, context);
        ScoreItemCaseMatchPolicy.MatchResult resGen = policy.evaluate(genericCase, context);

        assertThat(resTg.score()).isGreaterThan(resGen.score());
        assertThat(resTg.matchReasons()).anyMatch(r -> r.contains("命中项目专属实体"));
    }

    @Test
    @DisplayName("大类冲突一票否决：纯技术评分项与纯商务案例互斥，评分为0且一票否决")
    void testMacroCategoryConflictVetoZeroScore() {
        StubCandidate commercialCase = new StubCandidate(
                1007L, "财务审计报告与资产证明", "商务要求",
                "提供近三年财务审计报告", "我方财务状况良好，提供经审计的财务审计报告",
                "IT", "央企", "WON", "COMMERCIAL_TERMS", 90
        );

        ScoreItemCaseMatchPolicy.ScoreItemContext technicalContext = new ScoreItemCaseMatchPolicy.ScoreItemContext(
                891L, "T-4", "系统高可用架构与技术方案",
                "投标人须提供双活容灾与微服务技术方案",
                "IT", "央企", null, null, null
        );

        ScoreItemCaseMatchPolicy.MatchResult result = policy.evaluate(commercialCase, technicalContext);

        assertThat(result.score()).isEqualTo(0);
        assertThat(result.matchLabel()).isEqualTo("一般");
        assertThat(result.matchReasons()).contains("大类冲突一票否决");
    }

    @Test
    @DisplayName("大类冲突一票否决：资质证书类评分项与技术方案案例互斥，绝不允许方案匹配给证书项")
    void testQualificationScoreItemVetoesTechnicalCase() {
        StubCandidate technicalCase = new StubCandidate(
                1008L, "新品类的拓展增补方案", "技术方案",
                "品类拓展寻源要求", "西域凭借深厚的行业积淀与创新驱动的数智化能力，构建起系统化、专业化的寻源保障体系。",
                "工业品", "地方国企", "WON", "OTHER", 85
        );

        ScoreItemCaseMatchPolicy.ScoreItemContext qualContext = new ScoreItemCaseMatchPolicy.ScoreItemContext(
                892L, "1", "1 管理体系",
                "1 | 管理体系 | 1、评审标准：1）具有 ISO9001 质量管理体系认证、ISO14001环境管理体系认证、ISO45001职业健康安全管理体系认证的得10分。2）三项齐全得满分，缺少一项，不得分3）不满足或未提供证明文件者，不得分2、证明文件：投标人须提供在有效期的认证证书复印件或扫描件加盖投标人公章。 | 10.0 | 否",
                "工业品", "地方国企", null, "招商积余2026年维修物资战略采购项目", "招商积余"
        );

        ScoreItemCaseMatchPolicy.MatchResult result = policy.evaluate(technicalCase, qualContext);

        assertThat(result.score()).isEqualTo(0);
        assertThat(result.matchLabel()).isEqualTo("一般");
        assertThat(result.matchReasons()).contains("大类冲突一票否决");
    }

    @Test
    @DisplayName("业务子主题对齐：7 应急方案评分项应准确对齐 EMERGENCY_PLAN 并给应急措施案例高分匹配")
    void testEmergencyPlanScoreItemMatchesEmergencyCase() {
        StubCandidate emergencyCase = new StubCandidate(
                1009L, "（7）风险管理与应急措施", "技术方案",
                "制定完善的项目应急方案与售后服务保障", "针对平台订单紧急到货需求，设立紧急备件库与应急响应小组，提供快速缺货补货机制与应急处置承诺。",
                "工业品", "地方国企", "WON", "EMERGENCY_PLAN", 85
        );

        ScoreItemCaseMatchPolicy.ScoreItemContext emergencyContext = new ScoreItemCaseMatchPolicy.ScoreItemContext(
                893L, "7", "7 应急方案",
                "7 | 应急方案 | 1、评审标准：1）承诺在接到平台订单后5天内到货，缺货/补货的周期在7天内（提供承诺函）。2）评价所有涉及本项目的应急方案。评委横向比较：优9-10；良6-8；中3-5；差0-2。3）不满足或未提供证明文件者，不得分2、证明文件：投标人提供应急方案和承诺（投标单位自拟）。 | 10.0 | 否",
                "工业品", "地方国企", null, "招商积余2026年维修物资战略采购项目", "招商积余"
        );

        ScoreItemCaseMatchPolicy.MatchResult result = policy.evaluate(emergencyCase, emergencyContext);

        assertThat(result.score()).isGreaterThanOrEqualTo(60);
        assertThat(result.matchReasons()).anyMatch(r -> r.contains("同类业务子主题(应急预案与突发事件响应)") || r.contains("业务主题部分契合"));
    }

    @Test
    @DisplayName("大类穿透与一票否决：维度仅为'技术部分'但细则要求ISO认证证书时，必须归类为QUALIFICATION并一票否决技术方案")
    void testGenericTechDimWithQualificationDetailVetoesTechnicalCase() {
        StubCandidate technicalCase = new StubCandidate(
                1010L, "服务方案与技术响应", "技术方案",
                "技术方案与商城对接", "西域数智化商城对接方案与服务保障体系。",
                "工业品", "地方国企", "WON", "OTHER", 85
        );

        ScoreItemCaseMatchPolicy.ScoreItemContext qualContext = new ScoreItemCaseMatchPolicy.ScoreItemContext(
                895L, "2.2.5", "技术部分",
                "具有 ISO9001 质量管理体系认证、ISO14001环境管理体系认证，提供证书扫描件。",
                "工业品", "地方国企", null, "招商积余2026年维修物资战略采购项目", "招商积余"
        );

        ScoreItemCaseMatchPolicy.MatchResult result = policy.evaluate(technicalCase, qualContext);

        assertThat(result.score()).isEqualTo(0);
        assertThat(result.matchLabel()).isEqualTo("一般");
        assertThat(result.matchReasons()).contains("大类冲突一票否决");
    }

    @Test
    @DisplayName("根因回归：商品管理方案不得被物流仓储长文本凭泛词和评分档位模板反超定价/商品案例")
    void testProductPricingOutranksLongLogisticsCaseUnderCorpusIdf() {
        ScoreItemCaseMatchPolicy.ScoreItemContext context = new ScoreItemCaseMatchPolicy.ScoreItemContext(
                655L, "3", "商品管理方案",
                "3 | 商品管理方案 | 10 | SKU数量、商品的定价机制、价格管控机制、商品管理机制、及与竞争对手价格对标分析、竞争性定价方案与保障措施等。优秀得8-10分；一般得4-7分；较差得0-3分。",
                "工业品", "央企", null, "天山材料2026-2028年度MRO", "天山材料股份有限公司"
        );

        StubCandidate pricingCase = new StubCandidate(
                2001L, "西域自营产品定价机制", "技术",
                "商品定价与价格管控要求",
                "西域建立科学的产品定价机制与价格管控机制，围绕SKU成本、竞争对手价格对标分析制定竞争性定价方案。",
                "工业品", "地方国企", "FAILED"
        );
        StubCandidate logisticsCase = new StubCandidate(
                2002L, "物流配送及仓储能力", "物流配送及仓储能力",
                "3 | 仓储物流要求 | 10 | 优秀得8-10分；一般得4-7分；较差得0-3分。",
                "在供应保障方面构建中心仓、前置仓、配送站三级仓配体系，保障SKU数量与保障措施机制落实。" + "全国仓储物流配送网络覆盖与车辆调配体系建设。".repeat(30),
                "工业品", "央企", "WON"
        );
        List<StubCandidate> corpus = List.of(
                pricingCase,
                logisticsCase,
                new StubCandidate(2003L, "售后服务承诺", "技术", "售后保障", "7x24小时响应机制与保障措施", "工业品", "央企", "WON"),
                new StubCandidate(2004L, "应急预案", "技术", "应急响应", "突发事件保障措施与处置机制", "工业品", "央企", "WON"),
                new StubCandidate(2005L, "质量保证", "技术", "质量管控", "正品保障措施与验收机制", "工业品", "央企", "WON")
        );

        List<ScoreItemCaseMatchPolicy.MatchResult> results = policy.evaluateAll(corpus, context);

        assertThat(results.get(0).score()).isGreaterThan(results.get(1).score());
        assertThat(results.get(0).matchReasons()).anyMatch(r -> r.contains("商品管理与定价机制"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "3 | 商品管理方案 | 10 | 竞争性定价、价格对标分析",
            "| 3 | 商品管理方案 | 10 | 竞争性定价、价格对标分析 |",
            "3 | 商品管理方案 | 竞争性定价、价格对标分析 | 10.0 | 否",
            "商务技术（80） | 3 | 商品管理方案 | 10 | 竞争性定价、价格对标分析 |"
    })
    @DisplayName("表格条款保留正文：兼容尾部竖线及分值在正文之后的评分行")
    void testTableRequirementPreservesBodyForDetailOverlap(String detail) {
        assertThat(ScoreItemCaseTextMatchPolicy.calculateDetailOverlap(
                detail, "竞争性定价、价格对标分析", ScoreItemCaseTextMatchPolicy.Corpus.EMPTY))
                .isEqualTo(25);
        assertThat(ScoreItemCaseTextMatchPolicy.calculateDetailOverlap(
                detail, "商品管理方案", ScoreItemCaseTextMatchPolicy.Corpus.EMPTY))
                .isZero();
    }

    @Test
    @DisplayName("非评分表行的竖线分隔正文不能被当前缀剥离")
    void testPlainPipeSeparatedRequirementPreservesAllClauses() {
        assertThat(ScoreItemCaseTextMatchPolicy.calculateDetailOverlap(
                "竞争性定价 | 价格对标分析", "竞争性定价", ScoreItemCaseTextMatchPolicy.Corpus.EMPTY))
                .isPositive();
    }

    @Test
    @DisplayName("根因回归：主题完全不相关的案例不得仅凭类别/央企/中标等情境加成刷出高分")
    void testOffTopicCaseDoesNotGetFullContextBonus() {
        ScoreItemCaseMatchPolicy.ScoreItemContext context = new ScoreItemCaseMatchPolicy.ScoreItemContext(
                655L, "3", "商品管理方案",
                "SKU数量、商品的定价机制、价格管控机制及竞争性定价方案。",
                "工业品", "央企", null, null, null
        );
        StubCandidate unrelated = new StubCandidate(
                2006L, "车辆调度能力", "技术",
                "车辆调度与运输路线", "自有新能源运输车队覆盖华东区域干线配送。",
                "工业品", "央企", "WON", "LOGISTICS_WAREHOUSE", 90
        );

        ScoreItemCaseMatchPolicy.MatchResult result = policy.evaluate(unrelated, context);

        assertThat(result.score()).isLessThan(15);
    }

    @Test
    @DisplayName("低质废料切片与纯模板案例在打分层必须被一票否决门禁拦截")
    void testLowQualityTemplateCaseIsFilteredByDefenseInDepth() {
        ScoreItemCaseMatchPolicy.ScoreItemContext context = new ScoreItemCaseMatchPolicy.ScoreItemContext(
                101L, "1", "技术方案",
                "请提供技术保障与售后服务方案",
                "工业品", "央企", null, "三峡项目", "三峡集团"
        );
        StubCandidate templateCase = new StubCandidate(
                9999L, "法定代表人授权委托书格式", "技术",
                "授权书模板", "法定代表人授权委托书格式如下：本授权委托书声明：我______系三峡集团法定代表人，现授权张三参加三峡项目投标。特此委托。法定代表人（签字或盖章）：______ 代理人（签字）：______ 日期：____年__月__日",
                "工业品", "央企", "WON", "OTHER", 60
        );

        ScoreItemCaseMatchPolicy.MatchResult result = policy.evaluate(templateCase, context);

        assertThat(result.score()).isZero();
        assertThat(result.matchReasons()).contains("低质废料切片/纯模板过滤");
    }
}
