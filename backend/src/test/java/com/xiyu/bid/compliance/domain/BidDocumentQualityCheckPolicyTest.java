package com.xiyu.bid.compliance.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BidDocumentQualityCheckPolicy 单元测试
 * 覆盖25项检查规则的核心逻辑
 */
class BidDocumentQualityCheckPolicyTest {

    @Test
    void check_withCompleteDocument_shouldPassAllBasicChecks() {
        String document = """
                项目名称：智慧城市IOC项目
                投标单位：西域科技股份有限公司
                投标日期：2025年2月15日

                投标函
                报价：1,000,000元
                工期：90日历天
                质量承诺：符合国家质量标准

                法定代表人授权书

                营业执照
                统一社会信用代码：91310000XXXXXXXXXX

                原厂授权书：华为存储产品代理授权
                营业执照：统一社会信用代码 91310000XXXXXXXXXX
                安全生产许可证：有效期至2027年
                资质等级：一级

                联系方式：
                地址：上海市浦东新区
                电话：13800138000
                邮箱：contact@xiyu.com

                目录
                第1页 投标函（投标保证金：20万元，投标有效期：120天）
                第2页 技术方案

                开标时间：2026-07-01
                履约保证金：合同金额5%
                付款方式：货到验收后30%

                廉洁自律承诺书（已加盖公章）
                委托人身份证：110101199001011234
                授权委托书
                开标一览表（已盖章）
                投标保证金回单（基本账户汇出）
                CA证书编号：CA-2026-XXXX，有效期至2027-12-31
                保证金退款信息表
                保证金收据

                页眉：西域科技投标文件
                页脚：第1页/共10页

                1.1 项目概述
                1.2 技术方案

                图1 系统架构图
                表1 设备清单

                附件：业绩证明材料、财务报表、人员资质证明

                业绩：类似项目经验
                财务报表：审计报告
                人员简历：项目经理资格证书
                技术方案：实施方案
                """;

        BidDocumentQualityCheckPolicy.QualityCheckResult result =
                BidDocumentQualityCheckPolicy.check(document, "招标要求：一级资质");

        assertNotNull(result);
        assertEquals(25, result.issues().size());
        assertEquals(BidDocumentQualityCheckPolicy.OverallStatus.WARNING, result.overallStatus());
    }

    @Test
    void check_withEmptyDocument_shouldFailCriticalChecks() {
        String document = "";

        BidDocumentQualityCheckPolicy.QualityCheckResult result =
                BidDocumentQualityCheckPolicy.check(document, "");

        assertNotNull(result);
        assertEquals(BidDocumentQualityCheckPolicy.OverallStatus.FAIL, result.overallStatus());
        assertTrue(result.criticalCount() > 0);
    }

    @Test
    void checkCoverInfo_withCompleteInfo_shouldPass() {
        String content = "项目名称：测试项目\n投标单位：西域科技\n日期：2025-01-01";
        var issue = BidDocumentQualityCheckPolicy.checkCoverInfo(content);

        assertTrue(issue.passed());
        assertEquals(BidDocumentQualityCheckPolicy.IssueSeverity.PASS, issue.severity());
    }

    @Test
    void checkCoverInfo_withMissingInfo_shouldFail() {
        String content = "一些无关内容";
        var issue = BidDocumentQualityCheckPolicy.checkCoverInfo(content);

        assertFalse(issue.passed());
        assertEquals(BidDocumentQualityCheckPolicy.IssueSeverity.FAIL, issue.severity());
    }

    @Test
    void checkBidLetterFormat_withAllSections_shouldPass() {
        String content = "报价：100万元\n工期：60日历天\n质量承诺：合格";
        var issue = BidDocumentQualityCheckPolicy.checkBidLetterFormat(content);

        assertTrue(issue.passed());
    }

    @Test
    void checkBidLetterFormat_withMissingSections_shouldWarn() {
        String content = "只有报价：100万元";
        var issue = BidDocumentQualityCheckPolicy.checkBidLetterFormat(content);

        assertFalse(issue.passed());
        assertEquals(BidDocumentQualityCheckPolicy.IssueSeverity.WARNING, issue.severity());
    }

    @Test
    void checkAuthorizationLetter_withLetter_shouldPass() {
        String content = "法定代表人授权书";
        var issue = BidDocumentQualityCheckPolicy.checkAuthorizationLetter(content);

        assertTrue(issue.passed());
    }

    @Test
    void checkAuthorizationLetter_withoutLetter_shouldFail() {
        String content = "其他内容";
        var issue = BidDocumentQualityCheckPolicy.checkAuthorizationLetter(content);

        assertFalse(issue.passed());
        assertEquals(BidDocumentQualityCheckPolicy.IssueSeverity.FAIL, issue.severity());
    }

    @Test
    void checkPriceConsistency_withSameAmounts_shouldPass() {
        String content = "投标总价：1000000\n总报价：1000000";
        var issue = BidDocumentQualityCheckPolicy.checkPriceConsistency(content);

        assertTrue(issue.passed());
    }

    @Test
    void checkCurrencyUnit_withMixedUnits_shouldWarn() {
        String content = "报价100万元，单价5000元";
        var issue = BidDocumentQualityCheckPolicy.checkCurrencyUnit(content);

        assertFalse(issue.passed());
        assertEquals(BidDocumentQualityCheckPolicy.IssueSeverity.WARNING, issue.severity());
    }

    @Test
    void checkCurrencyUnit_withConsistentUnit_shouldPass() {
        String content = "报价1000000元";
        var issue = BidDocumentQualityCheckPolicy.checkCurrencyUnit(content);

        assertTrue(issue.passed());
    }

    @Test
    void checkContactInfo_withAllFields_shouldPass() {
        String content = "地址：上海\n电话：13800138000\n邮箱：test@example.com";
        var issue = BidDocumentQualityCheckPolicy.checkContactInfo(content);

        assertTrue(issue.passed());
    }

    @Test
    void checkContactInfo_withMissingFields_shouldWarn() {
        String content = "地址：上海";
        var issue = BidDocumentQualityCheckPolicy.checkContactInfo(content);

        assertFalse(issue.passed());
    }

    @Test
    void checkTocPageConsistency_withAuth_shouldPass() {
        String content = "原厂授权书\n营业执照\n安全生产许可证";
        var issue = BidDocumentQualityCheckPolicy.checkTocPageConsistency(content);

        assertTrue(issue.passed());
    }

    @Test
    void checkTocPageConsistency_withoutAuth_shouldFail() {
        String content = "1.1 概述\n1.2 方案";
        var issue = BidDocumentQualityCheckPolicy.checkTocPageConsistency(content);

        assertFalse(issue.passed());
    }

    @Test
    void checkPerformanceProof_withProof_shouldPass() {
        String content = "委托人身份证\n授权委托书";
        var issue = BidDocumentQualityCheckPolicy.checkPerformanceProof(content);

        assertTrue(issue.passed());
    }

    @Test
    void checkPerformanceProof_withoutProof_shouldFail() {
        String content = "这是一段完全无关的文字描述";
        var issue = BidDocumentQualityCheckPolicy.checkPerformanceProof(content);

        assertFalse(issue.passed());
        assertEquals(BidDocumentQualityCheckPolicy.IssueSeverity.FAIL, issue.severity());
    }

    @Test
    void checkPerformanceProof_withProofContainingXiyu_shouldPass() {
        String content = "法定代表人授权委托书\n受托人身份证";
        var issue = BidDocumentQualityCheckPolicy.checkPerformanceProof(content);

        assertTrue(issue.passed());
    }

    @Test
    void checkFinancialStatements_withStatements_shouldPass() {
        String content = "开标一览表";
        var issue = BidDocumentQualityCheckPolicy.checkFinancialStatements(content);

        assertTrue(issue.passed());
    }

    @Test
    void checkFinancialStatements_withoutStatements_shouldFail() {
        String content = "无财务内容";
        var issue = BidDocumentQualityCheckPolicy.checkFinancialStatements(content);

        assertFalse(issue.passed());
    }

    @Test
    void checkTechnicalProposal_withProposal_shouldPass() {
        String content = "保证金退款信息表\n保证金收据";
        var issue = BidDocumentQualityCheckPolicy.checkTechnicalProposal(content);

        assertTrue(issue.passed());
    }

    @Test
    void checkTechnicalProposal_withoutProposal_shouldFail() {
        String content = "无技术内容";
        var issue = BidDocumentQualityCheckPolicy.checkTechnicalProposal(content);

        assertFalse(issue.passed());
    }

    @Test
    void checkScheduleResponse_withKeywords_shouldFail() {
        var issue = BidDocumentQualityCheckPolicy.checkScheduleResponse("我方拒绝承担任何责任", "招标文件内容");

        assertFalse(issue.passed());
    }

    @Test
    void summarize_withCriticalIssues_shouldBeFail() {
        List<BidDocumentQualityCheckPolicy.QualityIssue> issues = List.of(
                new BidDocumentQualityCheckPolicy.QualityIssue("a", "A", BidDocumentQualityCheckPolicy.IssueSeverity.FAIL, "", "", false),
                new BidDocumentQualityCheckPolicy.QualityIssue("b", "B", BidDocumentQualityCheckPolicy.IssueSeverity.PASS, "", "", true)
        );

        var result = BidDocumentQualityCheckPolicy.summarize(issues);

        assertEquals(BidDocumentQualityCheckPolicy.OverallStatus.FAIL, result.overallStatus());
        assertEquals(1, result.criticalCount());
    }

    @Test
    void summarize_withOnlyWarnings_shouldBeWarning() {
        List<BidDocumentQualityCheckPolicy.QualityIssue> issues = List.of(
                new BidDocumentQualityCheckPolicy.QualityIssue("a", "A", BidDocumentQualityCheckPolicy.IssueSeverity.WARNING, "", "", false),
                new BidDocumentQualityCheckPolicy.QualityIssue("b", "B", BidDocumentQualityCheckPolicy.IssueSeverity.PASS, "", "", true)
        );

        var result = BidDocumentQualityCheckPolicy.summarize(issues);

        assertEquals(BidDocumentQualityCheckPolicy.OverallStatus.WARNING, result.overallStatus());
        assertEquals(0, result.criticalCount());
        assertEquals(1, result.warningCount());
    }

    @Test
    void summarize_withOnlyManual_shouldBeManual() {
        List<BidDocumentQualityCheckPolicy.QualityIssue> issues = List.of(
                new BidDocumentQualityCheckPolicy.QualityIssue("a", "A", BidDocumentQualityCheckPolicy.IssueSeverity.MANUAL, "", "", false),
                new BidDocumentQualityCheckPolicy.QualityIssue("b", "B", BidDocumentQualityCheckPolicy.IssueSeverity.PASS, "", "", true)
        );

        var result = BidDocumentQualityCheckPolicy.summarize(issues);

        assertEquals(BidDocumentQualityCheckPolicy.OverallStatus.MANUAL, result.overallStatus());
    }

    @Test
    void summarize_withAllPass_shouldBePass() {
        List<BidDocumentQualityCheckPolicy.QualityIssue> issues = List.of(
                new BidDocumentQualityCheckPolicy.QualityIssue("a", "A", BidDocumentQualityCheckPolicy.IssueSeverity.PASS, "", "", true),
                new BidDocumentQualityCheckPolicy.QualityIssue("b", "B", BidDocumentQualityCheckPolicy.IssueSeverity.PASS, "", "", true)
        );

        var result = BidDocumentQualityCheckPolicy.summarize(issues);

        assertEquals(BidDocumentQualityCheckPolicy.OverallStatus.PASS, result.overallStatus());
        assertEquals(0, result.criticalCount());
        assertEquals(0, result.warningCount());
    }

    @Test
    void containsAny_withMatchingKeyword_shouldReturnTrue() {
        assertTrue(BidDocumentQualityCheckPolicy.containsAny("hello world", "world"));
    }

    @Test
    void containsAny_withNoMatch_shouldReturnFalse() {
        assertFalse(BidDocumentQualityCheckPolicy.containsAny("hello world", "foo"));
    }

    @Test
    void containsAny_withNullContent_shouldReturnFalse() {
        assertFalse(BidDocumentQualityCheckPolicy.containsAny(null, "test"));
    }

    @Test
    void extractAmounts_shouldFindAllAmounts() {
        String content = "投标总价：1000000\n总报价：1000000\n合计金额：2000000";
        List<String> amounts = BidDocumentQualityCheckPolicy.extractAmounts(content);

        assertEquals(3, amounts.size());
    }

    @Test
    void allEqual_withSameValues_shouldReturnTrue() {
        assertTrue(BidDocumentQualityCheckPolicy.allEqual(List.of("100", "100", "100")));
    }

    @Test
    void allEqual_withDifferentValues_shouldReturnFalse() {
        assertFalse(BidDocumentQualityCheckPolicy.allEqual(List.of("100", "200")));
    }
}
