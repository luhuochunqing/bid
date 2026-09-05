package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BidDocumentQualityEngineTest {

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
    void testQualificationVeto_WhenMissingRequiredCertificate_Fails() {
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

        assertThat(vetoResult.status()).isEqualTo(QualityCheckStatus.FAIL);
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
                .withDepositRequirement(TenderRequirements.DepositRequirement.notRequired("前附表明确说明免收保证金"));

        List<QualityCheckItemResult> results = BidDocumentQualityEngine.evaluateAll(
                doc, DocumentContent.empty(), Collections.emptyList(), requirements, Set.of());

        assertThat(results).hasSize(25);

        QualityCheckItemResult depositReceipt = results.stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.DEPOSIT_RECEIPT.getCode()))
                .findFirst()
                .orElseThrow();
        assertThat(depositReceipt.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(depositReceipt.conclusion()).contains("免收/不涉及");

        QualityCheckItemResult depositRefund = results.stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.DEPOSIT_REFUND.getCode()))
                .findFirst()
                .orElseThrow();
        assertThat(depositRefund.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(depositRefund.conclusion()).contains("不涉及投标保证金");
    }

    @Test
    @DisplayName("标书端语义接通测试：招标要求缴纳且启用 LLM 时，Engine 必须调用 LLM 对 DEPOSIT_RECEIPT 执行语义裁决")
    void testEvaluateAll_WhenDepositRequired_InvokesLlmAdjudication() {
        String bidContent = "项目名称：测试采购\n投标人：西域\n2026年\n投标总价：100万元";
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash_bid", bidContent);

        TenderRequirements requirements = TenderRequirements.empty()
                .withDepositRequirement(TenderRequirements.DepositRequirement.required("50000元", null, "前附表要求50000元"));

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
                .withDepositRequirement(TenderRequirements.DepositRequirement.required("50000元", null, "要求50000元"));

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
                .withBidLetterRequirement(new TenderRequirements.BidLetterRequirement(
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
                .withBidLetterRequirement(new TenderRequirements.BidLetterRequirement(
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

        assertThat(authLetter.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(authLetter.conclusion()).contains("经大模型语义核查");
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
}
