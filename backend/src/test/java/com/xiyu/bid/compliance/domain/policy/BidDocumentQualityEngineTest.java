package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BidDocumentQualityEngineTest {

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "不得要求投标人具有ISO9001认证", "投标人须具备ISO9001认证或ITSS二级资质"})
    void unresolvedNegationOrAlternativeCannotBecomeModelPass(String condition) {
        var req = TenderRequirements.empty().withMergedQualifications(List.of(condition));
        var bid = DocumentContent.fromText("bid.pdf", "qualification-input", condition);
        var llm = new LlmAdjudicator(prompt -> LlmVerdict.success(new LlmVerdict("PASS", "", "比对通过", "")));
        var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(), req,
                Set.of(), llm, (p, s) -> { }).stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_MATCH.getCode())).findFirst().orElseThrow();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"PASS", "FAIL"})
    void unknownCertificateObligationCannotBeDecidedByModel(String decision) {
        var req = TenderRequirements.empty().withMergedQualifications(List.of("ISO9001认证"));
        var bid = DocumentContent.fromText("bid.pdf", "qualification-input", "资质材料\nISO9001认证");
        var llm = new LlmAdjudicator(prompt -> LlmVerdict.success(new LlmVerdict(decision, "", "模型给出的资质结论", "")));
        var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(), req,
                Set.of(), llm, (p, s) -> { }).stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_MATCH.getCode())).findFirst().orElseThrow();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    void modelPassCannotCertifyUnprovenCompleteQualificationConditions() {
        var req = TenderRequirements.empty().withMergedQualifications(List.of("独立法人", "近三年类似项目业绩"));
        var bid = DocumentContent.fromText("bid.pdf", "qualification-input", "投标函\n我方承诺响应招标要求。");
        var llm = new LlmAdjudicator(prompt -> LlmVerdict.success(
                new LlmVerdict("PASS", "", "资格全部满足", "")));
        var results = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(), req,
                Set.of(), llm, (p, s) -> { });
        assertThat(results.stream().filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                .findFirst().orElseThrow().status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    void evaluateAll_caRepositoryFailure_preservesIndependentPlaceholderFailure() {
        TenderRequirements req = TenderRequirements.empty().withCaCertificateRequirement(
                CaCertificateRequirement.requiredStandard("政采云", "使用CA数字证书"));
        List<QualityCheckItemResult> results = BidDocumentQualityEngine.evaluateAll(
                DocumentContent.fromText("bid.pdf", "hash", "CA数字证书\n【此处粘贴CA证书】"),
                DocumentContent.empty(), List.of(), req, Set.of(KnowledgeMatch.SOURCE_CA_CERTIFICATE));
        assertThat(results.stream().filter(r -> r.itemCode().equals("CA_CERTIFICATE")).findFirst().orElseThrow().status())
                .isEqualTo(QualityCheckStatus.FAIL);
    }

    @Test
    void evaluateAll_caRepositoryFailure_doesNotDeclareCertificateMissing() {
        TenderRequirements req = TenderRequirements.empty().withCaCertificateRequirement(
                CaCertificateRequirement.requiredStandard("招商局集团电子招标采购交易平台", "使用CA数字证书"));
        List<QualityCheckItemResult> results = BidDocumentQualityEngine.evaluateAll(
                DocumentContent.fromText("bid.pdf", "hash", "正常正文"), DocumentContent.empty(),
                List.of(), req, Set.of(KnowledgeMatch.SOURCE_CA_CERTIFICATE));
        assertThat(results.stream().filter(r -> r.itemCode().equals("CA_CERTIFICATE")).findFirst().orElseThrow().status())
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    @DisplayName("25项聚合裁决引擎必须精确输出 25 项有序结果")
    void testEvaluateAll_ReturnsExactly25ItemsInOrder() {
        String content = "项目名称：采购项目\n投标人：西域科技\n2026年\n投标函\n致：招标人\n法定代表人身份证明\n统一社会信用代码：91310000775244192P\n大写：人民币壹佰万元整 小写：1000000\n工期：30日历天\n（盖章）\n联系人：张三 电话：13800000000\n目录\n第1页\n第2页\n第3页\n承诺函\n身份证\n开标一览表\n投标保证金\nCA数字证书\n退还开户银行\n资质证书\n前附表\n技术方案\n答疑澄清";
        DocumentContent doc = DocumentContent.fromText("full.pdf", "hash_full", content);

        List<QualityCheckItemResult> results = BidDocumentQualityEngine.evaluateAll(
                doc, DocumentContent.fromText("tender.pdf", "hash_t", "招标文件内容"),
                Collections.emptyList(), TenderRequirements.empty(), Set.of());

        assertThat(results).hasSize(25);
        for (int i = 0; i < 25; i++) {
            assertThat(results.get(i).order()).isEqualTo(i + 1);
        }
    }

    @Test
    @DisplayName("一票否决项测试：提取的招标硬性要求 ISO27001 且标书缺失时断言 FAIL")
    void testQualificationVeto_WhenRequiredCertificateNotLocated_RequiresReview() {
        String tenderText = "一票否决条款：投标人必须具备 ISO27001 信息安全认证证书。";
        DocumentContent tenderDoc = DocumentContent.fromText("tender.pdf", "hash_t", tenderText);
        String bidContent = "项目名称：测试\n投标人：西域\n2026年\n具备 ISO9001 认证";
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash_bid", bidContent);
        TenderRequirements requirements = new TenderRequirements(
                List.of(), List.of("ISO27001 信息安全认证证书"), List.of(), List.of(), List.of(), false);

        List<KnowledgeMatch> kbMatches = List.of(new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "7",
                "资质证书: ISO9001质量管理体系认证（等级: 甲级）", true));

        List<QualityCheckItemResult> results = BidDocumentQualityEngine.evaluateAll(
                doc, tenderDoc, kbMatches, requirements, Set.of());

        QualityCheckItemResult vetoResult = results.stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                .findFirst()
                .orElseThrow();

        assertThat(vetoResult.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(vetoResult.conclusion()).contains("ISO27001");
        assertThat(vetoResult.evidences()).isNotEmpty();
    }

    @Test
    @DisplayName("实质性响应测试：标书中声明负偏离时判定 FAIL")
    void testResponsivenessReview_WhenNegativeDeviation_Fails() {
        String bidContent = "偏离表：付款方式存在负偏离，无法满足30天付款要求。";
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash_bid", bidContent);

        List<QualityCheckItemResult> results = BidDocumentQualityEngine.evaluateAll(
                doc, DocumentContent.fromText("tender.pdf", "hash_t", "招标要求"),
                Collections.emptyList(), TenderRequirements.empty(), Set.of());

        QualityCheckItemResult respResult = results.stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.RESPONSIVENESS_REVIEW.getCode()))
                .findFirst()
                .orElseThrow();

        assertThat(respResult.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(respResult.conclusion()).contains("实质性负偏离");
    }

    @Test
    @DisplayName("实质性响应回归：标书自述\"无负偏离/无不满足\"时不应误判 FAIL，应判定 PASS")
    void testResponsivenessReview_WhenExplicitlyNoDeviation_Passes() {
        String bidContent = "偏离表：付款方式无负偏离，无不满足项。";
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash_bid", bidContent);

        List<QualityCheckItemResult> results = BidDocumentQualityEngine.evaluateAll(
                doc, DocumentContent.fromText("tender.pdf", "hash_t", "招标要求"),
                Collections.emptyList(), TenderRequirements.empty(), Set.of());

        QualityCheckItemResult respResult = results.stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.RESPONSIVENESS_REVIEW.getCode()))
                .findFirst()
                .orElseThrow();

        assertThat(respResult.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(respResult.conclusion()).doesNotContain("存在实质性负偏离");
    }

    @Test
    @DisplayName("一票否决归一化回归：标书含带空格/标点的同一资格表述时不应误判缺失 FAIL")
    void testQualificationVeto_NormalizedText_SameCertificationNotFailed() {
        TenderRequirements requirements = new TenderRequirements(
                List.of(), List.of("ISO27001信息安全认证"), List.of(), List.of(), List.of(), false);
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash_bid",
                "项目名称：测试\n具备 ISO 27001 信息安全认证（有效期至2027年）");

        List<QualityCheckItemResult> results = BidDocumentQualityEngine.evaluateAll(
                doc, DocumentContent.empty(), Collections.emptyList(), requirements, Set.of());

        QualityCheckItemResult vetoResult = results.stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                .findFirst()
                .orElseThrow();

        // 第一阶段命中，无确定性硬信号 → 交 LLM lane（未启用则 MANUAL），不得武断 FAIL
        assertThat(vetoResult.status()).isNotEqualTo(QualityCheckStatus.FAIL);
    }

    @Test
    @DisplayName("降级测试：招标要求哨兵态时比对项 MANUAL + [REFERENCE_MISSING]，独立项照常")
    void testEvaluateAll_WhenRequirementsMissing_ComparisonItemsDegrade() {
        String bidContent = "项目名称：测试\n投标函\n偏离表：无负偏离";
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash_bid", bidContent);

        List<QualityCheckItemResult> results = BidDocumentQualityEngine.evaluateAll(
                doc, DocumentContent.empty(), Collections.emptyList(),
                TenderRequirements.missingSentinel(), Set.of());

        assertThat(results).hasSize(25);
        QualityCheckItemResult vetoResult = results.stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                .findFirst()
                .orElseThrow();
        assertThat(vetoResult.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(vetoResult.conclusion()).contains("[REFERENCE_MISSING]");

        // 独立项（实质性响应）不受哨兵态影响，照常裁决
        QualityCheckItemResult respResult = results.stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.RESPONSIVENESS_REVIEW.getCode()))
                .findFirst()
                .orElseThrow();
        assertThat(respResult.status()).isNotEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(respResult.conclusion()).doesNotContain("[REFERENCE_MISSING]");
    }

    @Test
    @DisplayName("保证金自适应测试：招标结构化抽取明确免收时，回单与退款信息项均自动判定 PASS（项目1358场景）")
    void testEvaluateAll_WhenDepositNotRequired_DepositItemsPassAutomatically() {
        String bidContent = "项目名称：测试采购\n投标人：西域\n2026年\n投标总价：100万元";
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash_bid", bidContent);

        TenderRequirements requirements = TenderRequirements.empty()
                .withDepositRequirement(DepositRequirement.notRequired("前附表明确说明免收保证金"));

        List<QualityCheckItemResult> results = BidDocumentQualityEngine.evaluateAll(
                doc, DocumentContent.empty(), Collections.emptyList(), requirements, Set.of());

        assertThat(results).hasSize(25);

        QualityCheckItemResult depositReceipt = results.stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.DEPOSIT_RECEIPT.getCode()))
                .findFirst()
                .orElseThrow();
        assertThat(depositReceipt.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(depositReceipt.conclusion()).startsWith("【不涉及】");
        assertThat(depositReceipt.conclusion()).contains("免收/不涉及");

        QualityCheckItemResult depositRefund = results.stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.DEPOSIT_REFUND.getCode()))
                .findFirst()
                .orElseThrow();
        assertThat(depositRefund.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(depositRefund.conclusion()).startsWith("【不涉及】");
        assertThat(depositRefund.conclusion()).contains("不涉及投标保证金/响应担保");
    }

    @Test
    @DisplayName("政采响应担保免收场景：招标文件包含'不要求递交响应担保'时，融合与Engine联动自动识别为【不涉及】直出PASS并免除退款信息表")
    void testDeposit_WhenGovernmentResponseGuaranteeNotRequired_PassesWithNotApplicablePrefix() {
        String tenderText = "第二章 供应商须知前附表\n| 3.4 | 响应担保 | 不要求递交响应担保 |\n| 3.5 | 响应有效期 | 90天 |";
        DocumentContent tenderDoc = DocumentContent.fromText("tender.pdf", "hash_tender", tenderText);
        DocumentContent bidDoc = DocumentContent.fromText("bid.docx", "hash_bid", "第五章 响应担保\n本项目不适用");

        // 经 DeterministicRequirementsFuser 融合后的招标要求
        TenderRequirements requirements = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(), tenderDoc);
        assertThat(requirements.depositRequirement()).isNotNull();
        assertThat(requirements.depositRequirement().required()).isFalse();

        List<QualityCheckItemResult> results = BidDocumentQualityEngine.evaluateAll(
                bidDoc, tenderDoc, Collections.emptyList(), requirements, Set.of());

        QualityCheckItemResult depositReceipt = results.stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.DEPOSIT_RECEIPT.getCode()))
                .findFirst()
                .orElseThrow();
        assertThat(depositReceipt.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(depositReceipt.conclusion()).startsWith("【不涉及】");
        assertThat(depositReceipt.conclusion()).contains("响应担保");

        QualityCheckItemResult depositRefund = results.stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.DEPOSIT_REFUND.getCode()))
                .findFirst()
                .orElseThrow();
        assertThat(depositRefund.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(depositRefund.conclusion()).startsWith("【不涉及】");
        assertThat(depositRefund.conclusion()).contains("不涉及投标保证金/响应担保");
    }

    @Test
    @DisplayName("标书端语义接通测试：招标要求缴纳且启用 LLM 时，Engine 必须调用 LLM 对 DEPOSIT_RECEIPT 执行语义裁决")
    void testEvaluateAll_WhenDepositRequired_InvokesLlmAdjudication() {
        String bidContent = "项目名称：测试采购\n投标人：西域\n2026年\n投标总价：100万元";
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash_bid", bidContent);

        TenderRequirements requirements = TenderRequirements.empty()
                .withDepositRequirement(DepositRequirement.required("50000元", null, "前附表要求50000元"));

        // 模拟 LLM 裁决通道
        LlmAdjudicator mockLlm = new LlmAdjudicator(prompt -> {
            if (prompt.contains("裁决检查项「" + BidQualityCheckItem.DEPOSIT_RECEIPT.getTitle() + "」")) {
                return LlmVerdict.success(
                        new LlmVerdict("FAIL", "", "经大模型核查，标书中未附带银行回单事实凭证", "请补充上传回执单"));
            }
            return LlmVerdict.success(
                    new LlmVerdict("PASS", "", "比对通过", ""));
        });

        List<QualityCheckItemResult> results = BidDocumentQualityEngine.evaluateAll(
                doc, DocumentContent.empty(), Collections.emptyList(), requirements, Set.of(), mockLlm, (p, s) -> { });

        QualityCheckItemResult depositReceipt = results.stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.DEPOSIT_RECEIPT.getCode()))
                .findFirst()
                .orElseThrow();

        // 验证 LLM 裁决生效
        assertThat(depositReceipt.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(depositReceipt.conclusion()).contains("经大模型核查");
    }

    @Test
    @DisplayName("大模型详尽证据保留测试：LLM比对出免收条款与不适用声明时，保留详细章节证据并冠以【不涉及】前缀，退款信息表联动免除")
    void testEvaluateAll_WhenLlmAdjudicatesNotApplicable_PreservesDetailedClausesAndPrefixesNotApplicable() {
        String bidContent = "文件五 响应担保（如有）\n本项目不适用";
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash_bid", bidContent);

        String tenderText = "第二章 供应商须知前附表\n3.4 响应担保：不要求递交响应担保";
        DocumentContent tenderDoc = DocumentContent.fromText("tender.pdf", "hash_tender", tenderText);
        TenderRequirements requirements = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(), tenderDoc);

        String detailedLlmConclusion = "招标文件第二章“供应商须知前附表”第3.4款明确规定“不要求递交响应担保”（即免收投标保证金）。投标文件在“文件五 响应担保（如有）”章节中明确标注“本项目不适用”，符合招标文件免收保证金的要求，故判定为通过。";

        LlmAdjudicator mockLlm = new LlmAdjudicator(prompt -> {
            if (prompt.contains("裁决检查项「" + BidQualityCheckItem.DEPOSIT_RECEIPT.getTitle() + "」")) {
                return LlmVerdict.success(
                        new LlmVerdict("PASS", "", detailedLlmConclusion, ""));
            }
            return LlmVerdict.success(
                    new LlmVerdict("PASS", "", "比对通过", ""));
        });

        List<QualityCheckItemResult> results = BidDocumentQualityEngine.evaluateAll(
                doc, tenderDoc, Collections.emptyList(), requirements, Set.of(), mockLlm, (p, s) -> { });

        QualityCheckItemResult depositReceipt = results.stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.DEPOSIT_RECEIPT.getCode()))
                .findFirst()
                .orElseThrow();

        assertThat(depositReceipt.status()).isEqualTo(QualityCheckStatus.PASS);
        // 验证保留了完整详实依据，且规范化加上了【不涉及】前缀
        assertThat(depositReceipt.conclusion()).startsWith("【不涉及】");
        assertThat(depositReceipt.conclusion()).contains("第3.4款明确规定“不要求递交响应担保”");
        assertThat(depositReceipt.conclusion()).contains("“文件五 响应担保（如有）”章节中明确标注“本项目不适用”");

        // 验证退款信息表联动免除
        QualityCheckItemResult depositRefund = results.stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.DEPOSIT_REFUND.getCode()))
                .findFirst()
                .orElseThrow();
        assertThat(depositRefund.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(depositRefund.conclusion()).startsWith("【不涉及】");
        assertThat(depositRefund.conclusion()).contains("不涉及投标保证金/响应担保");
    }

    @Test
    @DisplayName("强凭证直通测试：标书已附真实银行电子回单时，确定性强通过直通，无需调用 LLM，降低成本与抗抖动")
    void testEvaluateAll_WhenStrongVoucherPresent_PassesThroughWithoutLlmOverriding() {
        String tenderText = "前附表：投标保证金为人民币50000元。";
        DocumentContent tenderDoc = DocumentContent.fromText("tender.pdf", "hash_t", tenderText);

        String bidVoucherPage = """
                中国工商银行电子回单
                业务流水号：2026090510293847
                交易类型：跨行转账汇款   转账成功
                付款人户名：西域智慧供应链（上海）股份公司
                收款人户名：某某项目招标代理中心
                转账金额：人民币伍万元整（¥50,000.00）
                回单专用章：[电子印章 91310000]
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash_bid", bidVoucherPage);

        TenderRequirements requirements = TenderRequirements.empty()
                .withDepositRequirement(DepositRequirement.required("50000元", null, "要求50000元"));

        java.util.concurrent.atomic.AtomicInteger llmDepositCallCount = new java.util.concurrent.atomic.AtomicInteger(0);
        LlmAdjudicator llm = new LlmAdjudicator(prompt -> {
            if (prompt.contains("裁决检查项「" + BidQualityCheckItem.DEPOSIT_RECEIPT.getTitle() + "」")) {
                llmDepositCallCount.incrementAndGet();
                return LlmVerdict.success(new LlmVerdict("FAIL", "", "LLM误判覆盖", ""));
            }
            return LlmVerdict.success(new LlmVerdict("PASS", "", "通过", ""));
        });

        List<QualityCheckItemResult> results = BidDocumentQualityEngine.evaluateAll(
                doc, tenderDoc, Collections.emptyList(), requirements, Set.of(), llm, (p, s) -> { });

        QualityCheckItemResult depositReceipt = results.stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.DEPOSIT_RECEIPT.getCode()))
                .findFirst()
                .orElseThrow();

        assertThat(llmDepositCallCount.get())
                .as("标书已包含确定性真实凭证时，坚决不调 LLM，防止抖动与成本浪费")
                .isEqualTo(0);
        assertThat(depositReceipt.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(depositReceipt.conclusion()).contains("已附投标保证金汇款回单或电子保函凭证");
        assertThat(depositReceipt.evidences()).isNotEmpty();
    }

    @Test
    @DisplayName("投标函强凭证直通测试：标书已包含规范投标函正文与要素时，确定性强通过直通，无需调用 LLM，降低成本与抗抖动")
    void testEvaluateAll_WhenStrongBidLetterPresent_PassesThroughWithoutLlmOverriding() {
        String tenderText = "前附表：提交投标文件包括投标函，投标有效期为90日历天。";
        DocumentContent tenderDoc = DocumentContent.fromText("tender.pdf", "hash_t", tenderText);

        String bidPage = """
                投标文件 文件二  报价函
                致：上海西域机电系统有限公司：
                我方已仔细研究了贵司招标文件的全部条款，愿意遵照招标文件要求交付全部货物。
                我方承诺在招标文件规定的投标有效期90日历天内不修改、不撤销投标文件。
                供应商：西域智慧供应链（上海）股份公司（盖公章）
                法定代表人或授权代表（签字）：王五
                日期：2026年09月05日
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash_bid", bidPage);

        TenderRequirements requirements = TenderRequirements.empty()
                .withBidLetterRequirement(new BidLetterRequirement(
                        "报价函", "上海西域机电系统有限公司", 90, "格式为报价函，有效期90天"));

        java.util.concurrent.atomic.AtomicInteger llmBidLetterCallCount = new java.util.concurrent.atomic.AtomicInteger(0);
        LlmAdjudicator llm = new LlmAdjudicator(prompt -> {
            if (prompt.contains("裁决检查项「" + BidQualityCheckItem.BID_LETTER_FORMAT.getTitle() + "」")) {
                llmBidLetterCallCount.incrementAndGet();
                return LlmVerdict.success(new LlmVerdict("FAIL", "", "LLM误判覆盖", ""));
            }
            return LlmVerdict.success(new LlmVerdict("PASS", "", "通过", ""));
        });

        List<QualityCheckItemResult> results = BidDocumentQualityEngine.evaluateAll(
                doc, tenderDoc, Collections.emptyList(), requirements, Set.of(), llm, (p, s) -> { });

        QualityCheckItemResult bidLetter = results.stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.BID_LETTER_FORMAT.getCode()))
                .findFirst()
                .orElseThrow();

        assertThat(llmBidLetterCallCount.get())
                .as("标书已包含规范完备的投标函正文时，坚决不调 LLM，防止抖动与成本浪费")
                .isEqualTo(0);
        assertThat(bidLetter.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(bidLetter.conclusion()).contains("投标函/报价函格式规范，要素完备");
        assertThat(bidLetter.evidences()).isNotEmpty();
    }

    @Test
    @DisplayName("投标函存疑裁决接通测试：标书仅在目录页出现投标函被拦截时，成功接通 LLM 深度语义裁决")
    void testEvaluateAll_WhenBidLetterSuspicious_CallsLlmAdjudication() {
        String tocPage = """
                目录
                一、投标函及投标函附录 ................................. 1
                二、法定代表人身份证明 ................................. 5
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash_toc", tocPage);

        TenderRequirements requirements = TenderRequirements.empty()
                .withBidLetterRequirement(new BidLetterRequirement(
                        "投标函", "上海西域机电系统有限公司", 90, "要求投标函"));

        LlmAdjudicator mockLlm = new LlmAdjudicator(prompt -> {
            if (prompt.contains("裁决检查项「" + BidQualityCheckItem.BID_LETTER_FORMAT.getTitle() + "」")) {
                return LlmVerdict.success(
                        new LlmVerdict("FAIL", "", "经大模型语义核查，标书中仅在目录索引中出现投标函，缺失实质正文章节", "请在投标文件中补充标准格式的投标函正文"));
            }
            return LlmVerdict.success(new LlmVerdict("PASS", "", "通过", ""));
        });

        List<QualityCheckItemResult> results = BidDocumentQualityEngine.evaluateAll(
                doc, DocumentContent.empty(), Collections.emptyList(), requirements, Set.of(), mockLlm, (p, s) -> { });

        QualityCheckItemResult bidLetter = results.stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.BID_LETTER_FORMAT.getCode()))
                .findFirst()
                .orElseThrow();

        assertThat(bidLetter.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(bidLetter.conclusion()).contains("经大模型语义核查");
    }

    @Test
    @DisplayName("授权委托书强凭证直通测试：标书具备要素齐全的真实授权委托书正文时，直通 PASS 坚决不调用 LLM")
    void testEvaluateAll_WhenAuthLetterPass_BypassesLlmAdjudication() {
        String authPage = """
                法定代表人授权委托书
                本授权委托书声明：上海西域机电系统有限公司法定代表人张三，授权本公司员工李四（身份证号：110101199001015678）为委托代理人。
                代理人全权代表本公司签署投标文件，处理一切与某某采购项目投标有关事务。
                授权范围：签署投标文件
                授权期限：自签署之日起至投标有效期结束。
                
                法定代表人（签字）：张三
                委托代理人（签字）：李四
                上海西域机电系统有限公司（盖章）
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash_auth", authPage);

        java.util.concurrent.atomic.AtomicInteger llmAuthCallCount = new java.util.concurrent.atomic.AtomicInteger();
        LlmAdjudicator llm = new LlmAdjudicator(prompt -> {
            if (prompt.contains("裁决检查项「" + BidQualityCheckItem.AUTHORIZATION_LETTER.getTitle() + "」")) {
                llmAuthCallCount.incrementAndGet();
            }
            return LlmVerdict.success(new LlmVerdict("PASS", "", "通过", ""));
        });

        List<QualityCheckItemResult> results = BidDocumentQualityEngine.evaluateAll(
                doc, DocumentContent.empty(), Collections.emptyList(), TenderRequirements.empty(), Set.of(), llm, (p, s) -> { });

        QualityCheckItemResult authLetter = results.stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.AUTHORIZATION_LETTER.getCode()))
                .findFirst()
                .orElseThrow();

        assertThat(llmAuthCallCount.get())
                .as("标书已包含要素齐全的真实授权委托书正文时，坚决不调 LLM，防止抖动与浪费")
                .isEqualTo(0);
        assertThat(authLetter.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(authLetter.conclusion()).contains("已提供法定代表人授权委托书");
        assertThat(authLetter.evidences()).isNotEmpty();
    }

    @Test
    @DisplayName("授权委托书存疑裁决接通测试：标书仅在目录页出现条目被拦截时，成功接通 LLM 深度语义裁决")
    void testEvaluateAll_WhenAuthLetterSuspicious_CallsLlmAdjudication() {
        String tocPage = """
                目录
                第一章 投标函及投标函附录 ................. 1
                第二章 法定代表人身份证明及授权委托书 ..... 5
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash_toc", tocPage);

        LlmAdjudicator mockLlm = new LlmAdjudicator(prompt -> {
            if (prompt.contains("裁决检查项「" + BidQualityCheckItem.AUTHORIZATION_LETTER.getTitle() + "」")) {
                return LlmVerdict.success(
                        new LlmVerdict("FAIL", "", "经大模型语义核查，授权委托书仅存在于目录索引中，正文缺失实际授权书", "请在投标文件中补充授权委托书正文"));
            }
            return LlmVerdict.success(new LlmVerdict("PASS", "", "通过", ""));
        });

        List<QualityCheckItemResult> results = BidDocumentQualityEngine.evaluateAll(
                doc, DocumentContent.empty(), Collections.emptyList(), TenderRequirements.empty(), Set.of(), mockLlm, (p, s) -> { });

        QualityCheckItemResult authLetter = results.stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.AUTHORIZATION_LETTER.getCode()))
                .findFirst()
                .orElseThrow();

        assertThat(authLetter.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(authLetter.conclusion()).contains(ManualReasons.EVIDENCE_INSUFFICIENT);
    }

    @Test
    @DisplayName("营业执照强凭证直通测试：标书具备包含18位代码与主体要素的营业执照正文时，直通 PASS 坚决不调用 LLM")
    void testEvaluateAll_WhenBusinessLicensePass_BypassesLlmAdjudication() {
        String licensePage = """
                资质证明文件
                营业执照（副本复印件）
                统一社会信用代码：91310115MA1K4B9X8Y
                名称：上海西域数智供应链有限公司
                类型：有限责任公司
                法定代表人：叶永清
                经营范围：工业品销售、数智化供应链管理系统开发及技术咨询
                营业期限：2015年03月12日至长期
                登记机关：上海市浦东新区市场监督管理局
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash_license", licensePage);

        java.util.concurrent.atomic.AtomicInteger llmLicenseCallCount = new java.util.concurrent.atomic.AtomicInteger();
        LlmAdjudicator llm = new LlmAdjudicator(prompt -> {
            if (prompt.contains("裁决检查项「" + BidQualityCheckItem.BUSINESS_LICENSE.getTitle() + "」")) {
                llmLicenseCallCount.incrementAndGet();
            }
            return LlmVerdict.success(new LlmVerdict("PASS", "", "通过", ""));
        });

        List<QualityCheckItemResult> results = BidDocumentQualityEngine.evaluateAll(
                doc, DocumentContent.empty(), Collections.emptyList(), TenderRequirements.empty(), Set.of(), llm, (p, s) -> { });

        QualityCheckItemResult licenseResult = results.stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.BUSINESS_LICENSE.getCode()))
                .findFirst()
                .orElseThrow();

        assertThat(llmLicenseCallCount.get())
                .as("标书已包含要素齐全的真实营业执照正文时，坚决不调 LLM，防止抖动与浪费")
                .isEqualTo(0);
        assertThat(licenseResult.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(licenseResult.conclusion()).contains("已包含营业执照");
        assertThat(licenseResult.evidences()).isNotEmpty();
    }

    @Test
    @DisplayName("营业执照存疑裁决接通测试：标书仅在目录页出现条目被拦截时，成功接通 LLM 深度语义裁决")
    void testEvaluateAll_WhenBusinessLicenseSuspicious_CallsLlmAdjudication() {
        String tocPage = """
                目录
                第一章 投标函及投标函附录 ................. 1
                第二章 营业执照及资质证书 ................. 5
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash_toc", tocPage);

        LlmAdjudicator mockLlm = new LlmAdjudicator(prompt -> {
            if (prompt.contains("裁决检查项「" + BidQualityCheckItem.BUSINESS_LICENSE.getTitle() + "」")) {
                return LlmVerdict.success(
                        new LlmVerdict("FAIL", "", "经大模型语义核查，营业执照仅存在于目录索引中，正文缺失实际证照扫描件", "请在投标文件中补充营业执照扫描件"));
            }
            return LlmVerdict.success(new LlmVerdict("PASS", "", "通过", ""));
        });

        List<QualityCheckItemResult> results = BidDocumentQualityEngine.evaluateAll(
                doc, DocumentContent.empty(), Collections.emptyList(), TenderRequirements.empty(), Set.of(), mockLlm, (p, s) -> { });

        QualityCheckItemResult licenseResult = results.stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.BUSINESS_LICENSE.getCode()))
                .findFirst()
                .orElseThrow();

        assertThat(licenseResult.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(licenseResult.conclusion()).contains("经大模型语义核查");
    }

    @Test
    @DisplayName("身份证明强凭证直通测试：标书具备包含18位身份证号与有效期限等要素的真实证明时，直通 PASS 坚决不调用 LLM")
    void testEvaluateAll_WhenIdAuthPass_BypassesLlmAdjudication() {
        String idPage = """
                法定代表人身份证明
                投标人名称：上海西域数智供应链有限公司
                单位性质：有限责任公司
                成立时间：2015年03月12日
                经营期限：长期
                姓名：叶永清  性别：男  年龄：45岁  职务：总经理
                系上海西域数智供应链有限公司的法定代表人。
                公民身份号码：110101199003072345
                有效期限：2020.03.07-2040.03.07
                签发机关：上海市公安局浦东分局
                特此证明。
                投标人：上海西域数智供应链有限公司（盖章）
                2026年09月05日
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash_id", idPage);

        java.util.concurrent.atomic.AtomicInteger llmIdCallCount = new java.util.concurrent.atomic.AtomicInteger();
        LlmAdjudicator llm = new LlmAdjudicator(prompt -> {
            if (prompt.contains("裁决检查项「" + BidQualityCheckItem.ID_AUTH_LETTER.getTitle() + "」")) {
                llmIdCallCount.incrementAndGet();
            }
            return LlmVerdict.success(new LlmVerdict("PASS", "", "通过", ""));
        });

        List<QualityCheckItemResult> results = BidDocumentQualityEngine.evaluateAll(
                doc, DocumentContent.empty(), Collections.emptyList(), TenderRequirements.empty(), Set.of(), llm, (p, s) -> { });

        QualityCheckItemResult idResult = results.stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.ID_AUTH_LETTER.getCode()))
                .findFirst()
                .orElseThrow();

        assertThat(llmIdCallCount.get())
                .as("标书已包含要素齐全的真实身份证明与有效身份证件时，坚决不调 LLM，防止抖动与浪费")
                .isEqualTo(0);
        assertThat(idResult.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(idResult.conclusion()).contains("已包含有效身份证明与身份证扫描件");
        assertThat(idResult.evidences()).isNotEmpty();
    }

    @Test
    @DisplayName("身份证明存疑裁决接通测试：标书仅在目录页出现条目被拦截时，成功接通 LLM 深度语义裁决")
    void testEvaluateAll_WhenIdAuthSuspicious_CallsLlmAdjudication() {
        String tocPage = """
                目录
                第一章 投标函及投标函附录 ................. 1
                第二章 法定代表人身份证明 ................. 5
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash_toc", tocPage);

        LlmAdjudicator mockLlm = new LlmAdjudicator(prompt -> {
            if (prompt.contains("裁决检查项「" + BidQualityCheckItem.ID_AUTH_LETTER.getTitle() + "」")) {
                return LlmVerdict.success(
                        new LlmVerdict("FAIL", "", "经大模型语义核查，身份证明仅存在于目录索引中，正文缺失实际身份证明及证件复印件", "请在投标文件中补充身份证明及身份证扫描件"));
            }
            return LlmVerdict.success(new LlmVerdict("PASS", "", "通过", ""));
        });

        List<QualityCheckItemResult> results = BidDocumentQualityEngine.evaluateAll(
                doc, DocumentContent.empty(), Collections.emptyList(), TenderRequirements.empty(), Set.of(), mockLlm, (p, s) -> { });

        QualityCheckItemResult idResult = results.stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.ID_AUTH_LETTER.getCode()))
                .findFirst()
                .orElseThrow();

        assertThat(idResult.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(idResult.conclusion()).contains(ManualReasons.EVIDENCE_INSUFFICIENT);
    }

    @Test
    @DisplayName("财务三表齐全不得短路资格性评审：QUALIFICATION_REVIEW 仍须交 LLM（覆盖业绩/信誉）")
    void testEvaluateAll_WhenFinancialStatementCompliant_QualificationReviewStillGoesToLlm() {
        String finPage = """
                上海西域供应链管理股份有限公司
                2024年度财务报表及审计报告
                一、审计意见：我们审计了后附的财务报表，包括2024年12月31日的资产负债表，
                2024年度的利润表、现金流量表以及相关财务报表附注。
                我们认为，后附的财务报表在所有重大方面按照企业会计准则的规定编制。
                普华永道中天会计师事务所（特殊普通合伙）
                （会计师事务所盖章）
                注册会计师：张审计、李复核
                二、资产负债表
                流动资产合计：100,000,000元，负债合计：50,000,000元，所有者权益合计：50,000,000元
                三、利润表
                营业收入：200,000,000元，净利润：20,000,000元
                四、现金流量表
                经营活动产生的现金流量净额：18,000,000元
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash_fin", finPage);
        TenderRequirements req = new TenderRequirements(
                List.of(), List.of(), List.of(), List.of(), List.of(),
                null, null, null, null, null,
                new FinancialRequirement(true, true, List.of("2024"), List.of("资产负债表", "利润表", "现金流量表"), true, "提供2024年度经审计的财务报告"),
                false);

        java.util.concurrent.atomic.AtomicInteger llmCallCount = new java.util.concurrent.atomic.AtomicInteger();
        LlmAdjudicator llm = new LlmAdjudicator(prompt -> {
            if (prompt.contains("裁决检查项「" + BidQualityCheckItem.QUALIFICATION_REVIEW.getTitle() + "」")) {
                llmCallCount.incrementAndGet();
                assertThat(prompt).contains("企业信誉").contains("类似项目业绩");
            }
            return LlmVerdict.success(new LlmVerdict("PASS", "", "经大模型语义核查通过", ""));
        });

        List<QualityCheckItemResult> results = BidDocumentQualityEngine.evaluateAll(
                doc, DocumentContent.empty(), Collections.emptyList(), req, Set.of(), llm, (p, s) -> { });

        QualityCheckItemResult qualResult = results.stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_REVIEW.getCode()))
                .findFirst()
                .orElseThrow();

        assertThat(llmCallCount.get())
                .as("财务三表不能代替资格性评审整项裁决，必须把 QUALIFICATION_REVIEW 交给 LLM")
                .isEqualTo(1);
        assertThat(qualResult.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(qualResult.conclusion()).contains("尚需人工确认");
        assertThat(qualResult.conclusion()).doesNotContain("已提供完整的财务会计报告");
    }

    @Test
    @DisplayName("资格审查第一阶段财务占位符反假拦截：标书存在未填占位符模板时，直接 FAIL 拦截")
    void testEvaluateAll_WhenFinancialStatementPlaceholder_QualificationReviewFailsDirectly() {
        String finPage = """
                财务状况证明材料
                【此处粘贴2024年度财务审计报告及资产负债表】
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash_placeholder", finPage);
        TenderRequirements req = new TenderRequirements(
                List.of(), List.of(), List.of(), List.of(), List.of(),
                null, null, null, null, null,
                new FinancialRequirement(true, true, List.of("2024"), List.of("资产负债表", "利润表", "现金流量表"), true, "提供2024年度经审计的财务报告"),
                false);

        List<QualityCheckItemResult> results = BidDocumentQualityEngine.evaluateAll(
                doc, DocumentContent.empty(), Collections.emptyList(), req, Set.of());

        QualityCheckItemResult qualResult = results.stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_REVIEW.getCode()))
                .findFirst()
                .orElseThrow();

        assertThat(qualResult.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(qualResult.conclusion()).contains("占位模板");
        assertThat(FinancialStatementCheckPolicy.isHardAntiFakeFail(qualResult)).isTrue();
    }

    @Test
    @DisplayName("占位模板页与真实三表并存：不得因残留模板把 QUALIFICATION_REVIEW 一票否决")
    void testEvaluateAll_WhenPlaceholderAndRealStatements_DoesNotFailQualificationReview() {
        DocumentContent doc = new DocumentContent("bid.pdf", "hash_mix", "", List.of(
                new DocumentContent.PageContent(10,
                        "第七章 财务状况证明\n【此处粘贴经会计师事务所审计的财务审计报告】",
                        List.of("财务状况证明")),
                new DocumentContent.PageContent(16,
                        "资产负债表\n编制单位：测试企业 2024年12月31日\n流动资产：100万\n资产总计：500万\n流动负债：50万\n负债合计：200万\n所有者权益：300万",
                        List.of("资产负债表")),
                new DocumentContent.PageContent(17,
                        "利润表\n2024年度\n营业收入：800万\n营业成本：500万\n营业利润：150万\n净利润：120万",
                        List.of("利润表")),
                new DocumentContent.PageContent(18,
                        "现金流量表\n2024年度\n经营活动产生的现金流量：80万\n投资活动产生的现金流量：-10万\n现金及现金等价物净增加额：20万",
                        List.of("现金流量表")),
                new DocumentContent.PageContent(15,
                        "审计报告\n立信会计师事务所\n审计意见：无保留意见。注册会计师签字盖章。",
                        List.of("审计报告"))
        ));
        TenderRequirements req = new TenderRequirements(
                List.of(), List.of(), List.of(), List.of(), List.of(),
                null, null, null, null, null,
                new FinancialRequirement(true, true, List.of("2024"), List.of("资产负债表", "利润表", "现金流量表"), true, "提供2024年度经审计的财务报告"),
                false);

        java.util.concurrent.atomic.AtomicInteger llmCallCount = new java.util.concurrent.atomic.AtomicInteger();
        LlmAdjudicator llm = new LlmAdjudicator(prompt -> {
            if (prompt.contains("裁决检查项「" + BidQualityCheckItem.QUALIFICATION_REVIEW.getTitle() + "」")) {
                llmCallCount.incrementAndGet();
            }
            return LlmVerdict.success(new LlmVerdict("MANUAL", "AMBIGUOUS", "需人工核验残留模板与报表正文", ""));
        });

        List<QualityCheckItemResult> results = BidDocumentQualityEngine.evaluateAll(
                doc, DocumentContent.empty(), Collections.emptyList(), req, Set.of(), llm, (p, s) -> { });

        QualityCheckItemResult qualResult = results.stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_REVIEW.getCode()))
                .findFirst()
                .orElseThrow();

        assertThat(qualResult.status()).isNotEqualTo(QualityCheckStatus.FAIL);
        assertThat(llmCallCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("招标免除财务要求：不得把 QUALIFICATION_REVIEW 整项标 PASS")
    void testEvaluateAll_WhenFinancialExempt_QualificationReviewStillGoesToLlm() {
        TenderRequirements req = new TenderRequirements(
                List.of(), List.of(), List.of(), List.of(), List.of(),
                null, null, null, null, null,
                FinancialRequirement.notRequired("小额采购免除财务审计报告"),
                false);

        java.util.concurrent.atomic.AtomicInteger llmCallCount = new java.util.concurrent.atomic.AtomicInteger();
        LlmAdjudicator llm = new LlmAdjudicator(prompt -> {
            if (prompt.contains("裁决检查项「" + BidQualityCheckItem.QUALIFICATION_REVIEW.getTitle() + "」")) {
                llmCallCount.incrementAndGet();
            }
            return LlmVerdict.success(new LlmVerdict("MANUAL", "AMBIGUOUS", "请人工核验业绩与信誉条款", ""));
        });

        List<QualityCheckItemResult> results = BidDocumentQualityEngine.evaluateAll(
                DocumentContent.fromText("bid.pdf", "hash_empty", "类似业绩合同：2023年供货"),
                DocumentContent.empty(), Collections.emptyList(), req, Set.of(), llm, (p, s) -> { });

        QualityCheckItemResult qualResult = results.stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_REVIEW.getCode()))
                .findFirst()
                .orElseThrow();

        assertThat(llmCallCount.get()).isEqualTo(1);
        assertThat(qualResult.conclusion()).doesNotContain("免除/不涉及");
    }

    @Test
    @DisplayName("营业执照免收测试：招标不要求证照时直接PASS且不接通LLM")
    void testEvaluateAll_WhenBusinessLicenseNotRequired_PassesWithoutLlm() {
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash_nolicense", "投标报价表 总价：100万元整");
        TenderRequirements requirements = TenderRequirements.empty().withBusinessLicenseRequirement(
                new BusinessLicenseRequirement(false, List.of(), null, "本项目不涉及主体资格证明"));

        java.util.concurrent.atomic.AtomicInteger llmLicenseCallCount = new java.util.concurrent.atomic.AtomicInteger();
        LlmAdjudicator llm = new LlmAdjudicator(prompt -> {
            if (prompt.contains("裁决检查项「" + BidQualityCheckItem.BUSINESS_LICENSE.getTitle() + "」")) {
                llmLicenseCallCount.incrementAndGet();
            }
            return LlmVerdict.success(new LlmVerdict("PASS", "", "通过", ""));
        });

        List<QualityCheckItemResult> results = BidDocumentQualityEngine.evaluateAll(
                doc, DocumentContent.empty(), Collections.emptyList(), requirements, Set.of(), llm, (p, s) -> { });

        QualityCheckItemResult licenseResult = results.stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.BUSINESS_LICENSE.getCode()))
                .findFirst()
                .orElseThrow();

        assertThat(llmLicenseCallCount.get())
                .as("招标明确不要求营业执照时，不得再接通 LLM 覆盖直接 PASS")
                .isEqualTo(0);
        assertThat(licenseResult.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(licenseResult.conclusion()).contains("未要求");
    }

    @Test
    @DisplayName("报价一致性测试：投标报价超过最高投标限价时，直接判定 FAIL 废标风险")
    void testEvaluateAll_WhenPriceExceedsMaxPrice_FailsDirectly() {
        String bidContent = """
                投标总价（大写）：人民币伍佰贰拾万元整
                投标总价（小写）：¥5,200,000.00
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash_price", bidContent);
        TenderRequirements req = new TenderRequirements(
                List.of(), List.of(), List.of(), List.of(), List.of(),
                null, null, null, null, null, null,
                new PriceRequirement(true, new BigDecimal("5000000.00"), "最高限价500万元", "总价", true, true, "最高投标限价500万元整"),
                false);

        List<QualityCheckItemResult> results = BidDocumentQualityEngine.evaluateAll(
                doc, DocumentContent.empty(), Collections.emptyList(), req, Set.of());

        QualityCheckItemResult priceResult = results.stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.PRICE_CONSISTENCY.getCode()))
                .findFirst()
                .orElseThrow();

        assertThat(priceResult.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(priceResult.conclusion()).contains("超过招标文件最高投标限价");
    }

    @Test
    @DisplayName("报价一致性测试：大写小写完全相符且未超最高限价，判定 PASS")
    void testEvaluateAll_WhenPriceConsistentAndWithinLimit_Passes() {
        String bidContent = """
                投标总价（大写）：人民币肆佰捌拾万元整
                投标总价（小写）：¥4,800,000.00
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash_price_pass", bidContent);
        TenderRequirements req = new TenderRequirements(
                List.of(), List.of(), List.of(), List.of(), List.of(),
                null, null, null, null, null, null,
                new PriceRequirement(true, new BigDecimal("5000000.00"), "最高限价500万元", "总价", true, true, "最高投标限价500万元整"),
                false);

        List<QualityCheckItemResult> results = BidDocumentQualityEngine.evaluateAll(
                doc, DocumentContent.empty(), Collections.emptyList(), req, Set.of());

        QualityCheckItemResult priceResult = results.stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.PRICE_CONSISTENCY.getCode()))
                .findFirst()
                .orElseThrow();

        assertThat(priceResult.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(priceResult.conclusion()).contains("未超过招标文件最高投标限价");
    }

    @Test
    @DisplayName("开标一览表反假测试：目录点线不被误认为有效正文，未提供实际表格判定 FAIL")
    void testEvaluateAll_WhenBidOpeningTableOnlyInCatalogDots_Fails() {
        String catalogPage = "目录\n三、开标一览表........................45\n四、分项报价表........................48";
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash_cat", catalogPage);

        List<QualityCheckItemResult> results = BidDocumentQualityEngine.evaluateAll(
                doc, DocumentContent.empty(), Collections.emptyList(), TenderRequirements.empty(), Set.of());

        QualityCheckItemResult tableResult = results.stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.BID_OPENING_TABLE.getCode()))
                .findFirst()
                .orElseThrow();

        assertThat(tableResult.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(tableResult.conclusion()).contains("仅在目录中检索到开标一览表条目");
    }

    @Test
    @DisplayName("开标一览表反假测试：检测到未填写占位符模板时，直接 FAIL 拦截")
    void testEvaluateAll_WhenBidOpeningTablePlaceholder_FailsDirectly() {
        String placeholderPage = "开标一览表\n【此处粘贴开标一览表及分项报价清单】";
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash_table_placeholder", placeholderPage);

        List<QualityCheckItemResult> results = BidDocumentQualityEngine.evaluateAll(
                doc, DocumentContent.empty(), Collections.emptyList(), TenderRequirements.empty(), Set.of());

        QualityCheckItemResult tableResult = results.stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.BID_OPENING_TABLE.getCode()))
                .findFirst()
                .orElseThrow();

        assertThat(tableResult.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(tableResult.conclusion()).contains("占位模板");
    }

    @Test
    @DisplayName("电子标CA证书：招标文件明确免除或纸质标时，直接直出 PASS")
    void testEvaluateAll_WhenCaCertificateNotRequired_PassesWithoutLlm() {
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash_ca_doc", "正文内容");
        TenderRequirements req = new TenderRequirements(
                List.of(), List.of(), List.of(), List.of(), List.of(),
                null, null, null, null, null, null, null,
                new CaCertificateRequirement(false, null, false, false, "纸质标免除CA数字证书要求"),
                false);

        List<QualityCheckItemResult> results = BidDocumentQualityEngine.evaluateAll(
                doc, DocumentContent.empty(), Collections.emptyList(), req, Set.of());

        QualityCheckItemResult caResult = results.stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.CA_CERTIFICATE.getCode()))
                .findFirst()
                .orElseThrow();

        assertThat(caResult.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(caResult.conclusion()).contains("未要求 CA 电子签章或数字证书");
    }

    @Test
    @DisplayName("电子标CA证书核查：通用电子标未指定平台时，台账在库证书安全降级为 MANUAL_REVIEW（防跨省误用）")
    void testEvaluateAll_WhenCaCertificateInStockInKb_ReturnsManualReviewForGenericBid() {
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash_ca_real", "正常投标文件正文内容");
        CaCertificateEvidence ca = new CaCertificateEvidence(
                301L, "ENTITY_CA", "公章+法人章", "CFCA",
                java.time.LocalDate.now().plusYears(1), "上海公共资源交易中心",
                "IN_STOCK", "ACTIVE", "张主管", "李四");
        List<KnowledgeMatch> matches = List.of(ca.toKnowledgeMatch(true));

        List<QualityCheckItemResult> results = BidDocumentQualityEngine.evaluateAll(
                doc, DocumentContent.empty(), matches, TenderRequirements.empty(), Set.of());

        QualityCheckItemResult caResult = results.stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.CA_CERTIFICATE.getCode()))
                .findFirst()
                .orElseThrow();

        assertThat(caResult.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(caResult.conclusion()).contains("待确认");
        assertThat(caResult.conclusion()).contains("请人工复核");
        assertThat(caResult.evidences()).isNotEmpty();
    }

    @Test
    @DisplayName("电子标CA证书核查：指定交易平台但台账未录入对应平台 CA 证书时判定 FAIL 并给出录入指引")
    void testEvaluateAll_WhenCaCertificatePlatformMissingInKb_Fails() {
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash_ca_cat", "正文无要求");
        CaCertificateRequirement req = CaCertificateRequirement.requiredStandard(
                "江苏政府采购网", "本项目须使用江苏政府采购网 CA 锁加密");
        TenderRequirements tr = new TenderRequirements(
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                null, null, null, null, null, null, null, req, false);

        List<QualityCheckItemResult> results = BidDocumentQualityEngine.evaluateAll(
                doc, DocumentContent.empty(), Collections.emptyList(), tr, Set.of());

        QualityCheckItemResult caResult = results.stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.CA_CERTIFICATE.getCode()))
                .findFirst()
                .orElseThrow();

        assertThat(caResult.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(caResult.conclusion()).contains("未在 CA 证书管理中检索到匹配【江苏政府采购网】");
        assertThat(caResult.suggestion()).contains("CA证书管理");
    }

    @Test
    @DisplayName("电子标CA证书反假：检测到未填写占位符模板时，直接 FAIL 拦截")
    void testEvaluateAll_WhenCaCertificatePlaceholder_FailsDirectly() {
        String placeholderPage = "CA数字证书及签章\n【此处粘贴CA数字证书及电子签章有效性证明】";
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash_ca_placeholder", placeholderPage);

        List<QualityCheckItemResult> results = BidDocumentQualityEngine.evaluateAll(
                doc, DocumentContent.empty(), Collections.emptyList(), TenderRequirements.empty(), Set.of());

        QualityCheckItemResult caResult = results.stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.CA_CERTIFICATE.getCode()))
                .findFirst()
                .orElseThrow();

        assertThat(caResult.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(caResult.conclusion()).contains("占位模板");
    }
}
