// Input: 真实与伪造的投标文件财务章节（含完整三表、目录点线干扰、占位模板未填、部分缺失等）
// Output: 验证 FinancialStatementCheckPolicy 要素核验、反假、目录过滤与完整材料人工复核
// Pos: compliance/domain/policy/ - 标书端财务状况核验契约测试
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialStatementCheckPolicyTest {

    @Test
    @DisplayName("完整三表与审计报告印章：材料齐全仍需人工确认，并保留真实页码证据链")
    void testCheck_WhenFullThreeStatementsAndAuditReport_RequiresConfirmation() {
        DocumentContent bidDoc = new DocumentContent(
                "bid.pdf",
                "hash_123",
                "",
                List.of(
                        new DocumentContent.PageContent(
                                15,
                                "审计报告\n立信会计师事务所（特殊普通合伙）\n我们审计了上海西域机电系统有限公司2021年度、2022年度、2023年度财务报表。\n审计意见：后附财务报表公允反映了财务状况。\n注册会计师：王某某、李某某（签字盖章）",
                                List.of("财务审计报告")),
                        new DocumentContent.PageContent(
                                16,
                                "资产负债表\n编制单位：上海西域机电系统有限公司 2023年12月31日\n流动资产：货币资金 1000万\n非流动资产：固定资产 4000万\n资产总计：5000万元\n流动负债：应付账款 500万\n负债合计：2000万元\n所有者权益：3000万元",
                                List.of("资产负债表")),
                        new DocumentContent.PageContent(
                                17,
                                "利润表\n编制单位：上海西域机电系统有限公司 2023年度\n一、营业收入：8000万元\n减：营业成本：5000万元\n二、营业利润：1500万元\n净利润：1200万元",
                                List.of("利润表")),
                        new DocumentContent.PageContent(
                                18,
                                "现金流量表\n编制单位：上海西域机电系统有限公司 2023年度\n经营活动产生的现金流量：销售商品收到的现金 8500万元\n投资活动产生的现金流量：-500万元\n现金及现金等价物净增加额：800万元",
                                List.of("现金流量表"))
                )
        );

        FinancialRequirement req = new FinancialRequirement(
                true, true, List.of("2021", "2022", "2023"), List.of("资产负债表", "利润表", "现金流量表"), true, "须提供近三年财务审计报告");

        QualityCheckItemResult result = FinancialStatementCheckPolicy.check(bidDoc, req);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.itemCode()).isEqualTo(BidQualityCheckItem.QUALIFICATION_REVIEW.getCode());
        assertThat(result.conclusion()).contains("尚需人工确认");
        assertThat(result.evidences()).hasSize(4);
        assertThat(result.evidences().stream().map(e -> e.pageNumber()).toList()).containsExactly(15, 16, 17, 18);
    }

    @Test
    @DisplayName("目录点线索引页干扰：严格过滤，不被目录点线欺骗通过")
    void testCheck_WhenCatalogPageWithDots_Ignored() {
        DocumentContent bidDoc = new DocumentContent(
                "bid.pdf",
                "hash_toc",
                "",
                List.of(
                        new DocumentContent.PageContent(
                                2,
                                "目 录\n一、投标函及附录 ............ 1\n二、授权委托书 ............ 5\n三、财务状况及经审计的财务审计报告 ............ 25\n3.1 资产负债表 ............ 26\n3.2 利润表 ............ 28\n3.3 现金流量表 ............ 30",
                                List.of("目 录"))
                )
        );

        QualityCheckItemResult result = FinancialStatementCheckPolicy.check(bidDoc, FinancialRequirement.standard());

        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.conclusion()).contains("未在投标文件中检索到财务状况证明");
    }

    @Test
    @DisplayName("未填占位符模板拦截：检测到【此处粘贴财务审计报告】时判定 FAIL 并反假告警")
    void testCheck_WhenUnfilledPlaceholder_Fails() {
        DocumentContent bidDoc = new DocumentContent(
                "bid.pdf",
                "hash_placeholder",
                "",
                List.of(
                        new DocumentContent.PageContent(
                                25,
                                "第七章 财务状况证明\n【此处粘贴经会计师事务所审计的财务审计报告】\n______年度资产负债表\n【财务报表附后】",
                                List.of("财务状况证明"))
                )
        );

        QualityCheckItemResult result = FinancialStatementCheckPolicy.check(bidDoc, FinancialRequirement.standard());

        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.conclusion()).contains("占位模板但未填写实际报表内容");
        assertThat(result.evidences()).isNotEmpty();
    }

    @Test
    @DisplayName("解析为免除仍须人工确认适用条件")
    void testCheck_WhenTenderExempts_RequiresConfirmation() {
        DocumentContent bidDoc = new DocumentContent("bid.pdf", "hash_empty", "", List.of());
        FinancialRequirement req = FinancialRequirement.notRequired("小额采购免除财务审计报告");

        QualityCheckItemResult result = FinancialStatementCheckPolicy.check(bidDoc, req);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.suggestion()).contains("豁免条件");
    }

    @Test
    @DisplayName("部分报表缺失（仅有资产负债表，缺失利润表与现金流量表）：判定 MANUAL 并提示人工核验")
    void testCheck_WhenPartialStatements_ManualReview() {
        DocumentContent bidDoc = new DocumentContent(
                "bid.pdf",
                "hash_partial",
                "",
                List.of(
                        new DocumentContent.PageContent(
                                20,
                                "资产负债表\n编制单位：测试企业 2023年12月31日\n流动资产：货币资金 100万\n资产总计：500万\n负债合计：200万\n所有者权益：300万",
                                List.of("资产负债表"))
                )
        );

        QualityCheckItemResult result = FinancialStatementCheckPolicy.check(bidDoc, FinancialRequirement.standard());

        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.conclusion()).contains("利润表", "现金流量表");
    }

    @Test
    @DisplayName("审计意见页仅点名三表、无表内要素：不得判定 PASS")
    void testCheck_WhenAuditOpinionNamesStatementsWithoutTables_DoesNotPass() {
        DocumentContent bidDoc = new DocumentContent(
                "bid.pdf",
                "hash_opinion_only",
                "",
                List.of(new DocumentContent.PageContent(
                        15,
                        "审计报告\n立信会计师事务所\n我们审计了后附的资产负债表、利润表、现金流量表。\n审计意见：无保留意见。注册会计师签字盖章。",
                        List.of("审计报告")))
        );

        QualityCheckItemResult result = FinancialStatementCheckPolicy.check(bidDoc, FinancialRequirement.standard());

        assertThat(result.status()).isNotEqualTo(QualityCheckStatus.PASS);
        assertThat(FinancialStatementCheckPolicy.isHardAntiFakeFail(result)).isFalse();
    }

    @Test
    @DisplayName("招标要求 2021-2023，标书只有 2023：年份未完全覆盖，不得 PASS")
    void testCheck_WhenRequiredYearsPartiallyCovered_DoesNotPass() {
        DocumentContent bidDoc = threeStatementsDoc("2023");
        FinancialRequirement req = new FinancialRequirement(
                true, true, List.of("2021", "2022", "2023"),
                List.of("资产负债表", "利润表", "现金流量表"), true, "近三年");

        QualityCheckItemResult result = FinancialStatementCheckPolicy.check(bidDoc, req);

        assertThat(result.status()).isNotEqualTo(QualityCheckStatus.PASS);
    }

    @Test
    @DisplayName("招标要求具体年份但报表页抽不到年份：不得真空通过")
    void testCheck_WhenRequiredYearsMissingFromText_DoesNotPass() {
        DocumentContent bidDoc = new DocumentContent("bid.pdf", "hash_no_year", "", List.of(
                new DocumentContent.PageContent(16,
                        "资产负债表\n流动资产：100\n资产总计：500\n流动负债：50\n负债合计：200\n所有者权益：300",
                        List.of("资产负债表")),
                new DocumentContent.PageContent(17,
                        "利润表\n营业收入：800\n营业成本：500\n营业利润：150\n净利润：120",
                        List.of("利润表")),
                new DocumentContent.PageContent(18,
                        "现金流量表\n经营活动产生的现金流量：80\n投资活动产生的现金流量：-10\n现金及现金等价物净增加额：20",
                        List.of("现金流量表")),
                new DocumentContent.PageContent(15,
                        "审计报告\n立信会计师事务所\n审计意见：无保留意见。注册会计师签字盖章。",
                        List.of("审计报告"))
        ));
        FinancialRequirement req = new FinancialRequirement(
                true, true, List.of("2023"), List.of("资产负债表", "利润表", "现金流量表"), true, "2023年度");

        QualityCheckItemResult result = FinancialStatementCheckPolicy.check(bidDoc, req);

        assertThat(result.status()).isNotEqualTo(QualityCheckStatus.PASS);
    }

    @Test
    @DisplayName("相对年份「近三年」不得仅凭字面短路为覆盖成功")
    void testCheck_WhenRelativeYearRequirement_DoesNotAutoPass() {
        DocumentContent bidDoc = threeStatementsDoc("2015");
        FinancialRequirement req = new FinancialRequirement(
                true, true, List.of("近三年"), List.of("资产负债表", "利润表", "现金流量表"), true, "近三年");

        QualityCheckItemResult result = FinancialStatementCheckPolicy.check(bidDoc, req);

        assertThat(result.status()).isNotEqualTo(QualityCheckStatus.PASS);
    }

    @Test
    @DisplayName("占位页与真实三表并存：政策层不得 FAIL")
    void testCheck_WhenPlaceholderAndRealStatements_DoesNotFail() {
        DocumentContent bidDoc = new DocumentContent("bid.pdf", "hash_mix", "", List.of(
                new DocumentContent.PageContent(10,
                        "【此处粘贴经会计师事务所审计的财务审计报告】",
                        List.of("模板")),
                new DocumentContent.PageContent(16,
                        "资产负债表\n编制单位：测试企业 2023年12月31日\n流动资产：100万\n资产总计：500万\n流动负债：50万\n负债合计：200万\n所有者权益：300万",
                        List.of("资产负债表")),
                new DocumentContent.PageContent(17,
                        "利润表\n2023年度\n营业收入：800万\n营业成本：500万\n营业利润：150万\n净利润：120万",
                        List.of("利润表")),
                new DocumentContent.PageContent(18,
                        "现金流量表\n2023年度\n经营活动产生的现金流量：80万\n投资活动产生的现金流量：-10万\n现金及现金等价物净增加额：20万",
                        List.of("现金流量表")),
                new DocumentContent.PageContent(15,
                        "审计报告\n立信会计师事务所\n我们审计了 2023 年度财务报表。审计意见：无保留意见。注册会计师签字盖章。",
                        List.of("审计报告"))
        ));

        QualityCheckItemResult result = FinancialStatementCheckPolicy.check(
                bidDoc, new FinancialRequirement(true, true, List.of("2023"),
                        List.of("资产负债表", "利润表", "现金流量表"), true, "2023"));

        assertThat(result.status()).isNotEqualTo(QualityCheckStatus.FAIL);
        assertThat(FinancialStatementCheckPolicy.isHardAntiFakeFail(result)).isFalse();
    }

    @Test
    @DisplayName("仅占位模板：isHardAntiFakeFail 为 true")
    void testCheck_WhenOnlyPlaceholder_HardAntiFakeFail() {
        DocumentContent bidDoc = new DocumentContent(
                "bid.pdf", "hash_ph", "",
                List.of(new DocumentContent.PageContent(25,
                        "【此处粘贴经会计师事务所审计的财务审计报告】", List.of())));

        QualityCheckItemResult result = FinancialStatementCheckPolicy.check(bidDoc, FinancialRequirement.standard());

        assertThat(FinancialStatementCheckPolicy.isHardAntiFakeFail(result)).isTrue();
        assertThat(result.conclusion()).startsWith("[UNFILLED_PLACEHOLDER]");
    }

    @Test
    @DisplayName("相对年份「近三年」即使报表含近年也不得规则层直通 PASS")
    void testCheck_WhenRelativeYearsAndRecentStatements_StillDoesNotPass() {
        DocumentContent bidDoc = threeStatementsDoc("2024");
        FinancialRequirement req = new FinancialRequirement(
                true, true, List.of("近三年"), List.of("资产负债表", "利润表", "现金流量表"), true, "近三年");

        QualityCheckItemResult result = FinancialStatementCheckPolicy.check(bidDoc, req);

        assertThat(result.status()).isNotEqualTo(QualityCheckStatus.PASS);
        assertThat(FinancialStatementCheckPolicy.yearsFullyCovered(List.of("近三年"), java.util.Set.of("2022", "2023", "2024")))
                .isFalse();
    }

    @Test
    @DisplayName("年份 token「2023年度」按绝对年规范化后可覆盖")
    void testYearsFullyCovered_NormalizesAnnualSuffix() {
        assertThat(FinancialStatementCheckPolicy.yearsFullyCovered(
                List.of("2023年度"), java.util.Set.of("2023"))).isTrue();
        assertThat(FinancialStatementCheckPolicy.yearsFullyCovered(
                List.of("2021", "2022", "2023"), java.util.Set.of("2023"))).isFalse();
        assertThat(FinancialStatementCheckPolicy.yearsFullyCovered(
                List.of("2023"), java.util.Set.of())).isFalse();
    }

    private static DocumentContent threeStatementsDoc(final String year) {
        return new DocumentContent("bid.pdf", "hash_" + year, "", List.of(
                new DocumentContent.PageContent(16,
                        "资产负债表\n编制单位：测试企业 " + year + "年12月31日\n流动资产：100万\n资产总计：500万\n流动负债：50万\n负债合计：200万\n所有者权益：300万",
                        List.of("资产负债表")),
                new DocumentContent.PageContent(17,
                        "利润表\n" + year + "年度\n营业收入：800万\n营业成本：500万\n营业利润：150万\n净利润：120万",
                        List.of("利润表")),
                new DocumentContent.PageContent(18,
                        "现金流量表\n" + year + "年度\n经营活动产生的现金流量：80万\n投资活动产生的现金流量：-10万\n现金及现金等价物净增加额：20万",
                        List.of("现金流量表")),
                new DocumentContent.PageContent(15,
                        "审计报告\n立信会计师事务所\n我们审计了" + year + "年度财务报表。审计意见：无保留意见。注册会计师签字盖章。",
                        List.of("审计报告"))
        ));
    }
}
