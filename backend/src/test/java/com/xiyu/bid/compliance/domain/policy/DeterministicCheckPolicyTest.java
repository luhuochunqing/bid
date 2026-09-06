package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicCheckPolicyTest {

    @Test
    @DisplayName("大写金额转换算法测试")
    void testConvertChineseToNumber() {
        assertThat(DeterministicCheckPolicy.convertChineseToNumber("壹佰万元"))
                .isEqualByComparingTo(BigDecimal.valueOf(1000000));
        assertThat(DeterministicCheckPolicy.convertChineseToNumber("伍拾万零伍佰元"))
                .isEqualByComparingTo(BigDecimal.valueOf(500500));
        assertThat(DeterministicCheckPolicy.convertChineseToNumber("叁仟捌佰万元整"))
                .isEqualByComparingTo(BigDecimal.valueOf(38000000));
    }

    @Test
    @DisplayName("报价一致性检查：大写与小写一致时断言 PASS")
    void testPriceConsistency_Pass() {
        String page1 = "投标文件封面\n项目：数智化采购平台\n投标人：西域供应链有限公司\n2026年08月18日";
        String page2 = "投标总价：\n大写：人民币壹佰万元整\n小写：¥1000000.00";
        DocumentContent doc = DocumentContent.fromText("test.pdf", "hash123", page1 + "\f" + page2);

        QualityCheckItemResult result = DeterministicCheckPolicy.checkPriceConsistency(doc);
        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(result.itemCode()).isEqualTo(BidQualityCheckItem.PRICE_CONSISTENCY.getCode());
        assertThat(result.evidences()).isNotEmpty();
        assertThat(result.evidences().get(0).pageNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("报价一致性检查：大写与小写不一致时断言 FAIL")
    void testPriceConsistency_Fail() {
        String page1 = "投标总价：大写：人民币壹仟万元整 (小写：¥1000000.00)";
        DocumentContent doc = DocumentContent.fromText("test.pdf", "hash123", page1);

        QualityCheckItemResult result = DeterministicCheckPolicy.checkPriceConsistency(doc);
        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.conclusion()).contains("不一致");
        assertThat(result.suggestion()).isNotEmpty();
    }

    @Test
    @DisplayName("封面完整性检查：包含项目、投标人、日期断言 PASS，缺失时 FAIL")
    void testCoverInfo() {
        String completeCover = "【投标文件】\n项目名称：某大型智慧中台采购\n投标人：西域科技有限公司\n日期：2026-08-18";
        DocumentContent passDoc = DocumentContent.fromText("bid.pdf", "hash1", completeCover);
        QualityCheckItemResult passResult = DeterministicCheckPolicy.checkCoverInfo(passDoc);
        assertThat(passResult.status()).isEqualTo(QualityCheckStatus.PASS);

        String incompleteCover = "【投标文件】\n投标人：西域科技有限公司";
        DocumentContent failDoc = DocumentContent.fromText("bid.pdf", "hash2", incompleteCover);
        QualityCheckItemResult failResult = DeterministicCheckPolicy.checkCoverInfo(failDoc);
        assertThat(failResult.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(failResult.conclusion()).contains("缺少必要关键要素");
    }

    @Test
    @DisplayName("敏感词与废标词扫描：明确违规词断言 FAIL 并定位物理页码")
    void testVetoKeyword_Hit() {
        String page1 = "第一章 概述\n项目正常实施";
        String page2 = "第二章 合作模式\n本项目部分技术将进行违法分包。";
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash1", page1 + "\f" + page2);

        QualityCheckItemResult result = DeterministicCheckPolicy.checkVetoKeyword(doc);
        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.evidences()).hasSize(1);
        assertThat(result.evidences().get(0).pageNumber()).isEqualTo(2);
        assertThat(result.evidences().get(0).quote()).contains("违法分包");
    }

    @Test
    @DisplayName("敏感词与废标词扫描：正向合规承诺与否定句式应豁免断言 PASS")
    void testVetoKeyword_Commitment_Pass() {
        String page1 = "承诺函\n我方承诺严禁转包，承诺不进行违法分包，杜绝弄虚作假与串通投标。";
        String page2 = "合同特别声明：因供应商违约严重不达标...买方有权解除合同并追究违约责任。";
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash_commit", page1 + "\f" + page2);

        QualityCheckItemResult result = DeterministicCheckPolicy.checkVetoKeyword(doc);
        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(result.conclusion()).contains("正向履约承诺已合规核验");
    }

    @Test
    @DisplayName("附件清单检查：招标文件免收保证金与不要求 CA 时断言 PASS")
    void testAttachmentChecks_TenderNotRequired_Pass() {
        String tenderText = "本项目免收投标保证金，采用纸质标书递交，无需缴纳保证金与电子 CA。";
        String bidText = "投标文件正文\n投标总价：100万元\n开户银行：招商银行上海分行\n账号：123456789";
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash_att", bidText);

        QualityCheckItemResult depositResult = DeterministicCheckPolicy.checkDepositReceipt(doc, tenderText);
        assertThat(depositResult.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(depositResult.conclusion()).contains("免收/不涉及");

        QualityCheckItemResult caResult = DeterministicCheckPolicy.checkCaCertificate(doc, tenderText);
        assertThat(caResult.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(caResult.conclusion()).contains("未要求 CA 电子签章");

        QualityCheckItemResult refundResult = DeterministicCheckPolicy.checkDepositRefund(doc, tenderText);
        assertThat(refundResult.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(refundResult.conclusion()).contains("不涉及投标保证金");
    }

    @Test
    @DisplayName("附件清单检查：招标文件前附表表格式免收（项目1358形态）断言 PASS")
    void testAttachmentChecks_Project1358TableFormat_Pass() {
        String tenderText = "投标人须知前附表：\n| 3.4.1 | 投标保证金 | 不适用 | 本项目不要求提交保证金 |";
        String bidText = "投标文件正文\n投标报价：100万元";
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash_1358", bidText);

        QualityCheckItemResult depositResult = DeterministicCheckPolicy.checkDepositReceipt(doc, tenderText);
        assertThat(depositResult.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(depositResult.conclusion()).contains("免收/不涉及");

        QualityCheckItemResult refundResult = DeterministicCheckPolicy.checkDepositRefund(doc, tenderText);
        assertThat(refundResult.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(refundResult.conclusion()).contains("不涉及投标保证金");
    }

    @Test
    @DisplayName("附件清单检查：招标要求缴纳时，标书纯目录/附件清单占位符不得误判 PASS（杜绝假通过）")
    void testAttachmentChecks_TocPlaceholder_DoesNotPass() {
        String tenderText = "前附表：投标保证金金额为人民币 50,000 元整。";
        String bidText = "目 录\n第一章 投标函\n第二章 报价明细\n附件七 投标保证金汇款凭证（如有）\n第 15 页";
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash_toc", bidText);

        QualityCheckItemResult depositResult = DeterministicCheckPolicy.checkDepositReceipt(doc, tenderText);
        assertThat(depositResult.status()).isNotEqualTo(QualityCheckStatus.PASS);
        assertThat(depositResult.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(depositResult.conclusion()).contains("无法确认为实际凭证");
    }

    @Test
    @DisplayName("附件清单检查：标书含真实银行回执流水要素断言 PASS")
    void testAttachmentChecks_RealVoucher_Pass() {
        String tenderText = "前附表：投标保证金金额为人民币 50,000 元整。";
        String bidText = "附件七 投标保证金缴纳凭证\n中国招商银行电子回单\n业务凭证号：202609051234567\n付款人账号：622588889999\n转账金额：50000.00元\n交易流水号：TRX99887766\n转账成功";
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash_real", bidText);

        QualityCheckItemResult depositResult = DeterministicCheckPolicy.checkDepositReceipt(doc, tenderText);
        assertThat(depositResult.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(depositResult.conclusion()).contains("已附投标保证金汇款回单");
    }

    @Test
    @DisplayName("营业执照与主体资格：识别 18 位统一社会信用代码")
    void testBusinessLicense_WithUSCC() {
        String page = "企业资质证明\n营业执照\n统一社会信用代码：91310000775244192P\n法定代表人：张三\n登记机关：上海市市场监督管理局\n西域供应链";
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash1", page);

        QualityCheckItemResult result = DeterministicCheckPolicy.checkBusinessLicense(doc);
        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(result.conclusion()).contains("91310000775244192P");
    }

    @Test
    @DisplayName("全量确定性规则执行：返回 18 项检查结果")
    void testExecuteAllChecks() {
        String content = "项目名称：采购项目\n投标人：西域科技\n2026年\n投标函\n致：招标人\n法定代表人身份证明\n统一社会信用代码：91310000775244192P\n大写：人民币壹佰万元整 小写：1000000\n工期：30日历天\n（盖章）\n联系人：张三 电话：13800000000\n目录\n第1页\n第2页\n第3页\n承诺函\n身份证\n开标一览表\n投标保证金\nCA数字证书\n退还开户银行";
        DocumentContent doc = DocumentContent.fromText("full.pdf", "hash_full", content);

        List<QualityCheckItemResult> results = DeterministicCheckPolicy.executeChecks(doc, "招标要求");
        assertThat(results).hasSize(18);
        assertThat(results.stream().allMatch(r -> r.status() != null)).isTrue();
    }

    @Test
    @DisplayName("证据定位：单页长文档命中关键词时，quote 取关键词附近上下文而非页首（分页缺失场景）")
    void evidenceQuote_locatesAroundKeyword_onLongSinglePage() {
        String cover = "张家口银行 XX 电商平台采购项目 投标文件 投标人：西域智慧供应链（上海）股份公司 2026年6月21日";
        String filler = "服务承诺与实施方案填充内容，无核查关键词。".repeat(400);
        String license = "第九章 资格证明文件\n营业执照\n统一社会信用代码：91310000775244192P\n法定代表人：张三\n营业期限：长期";
        String hugeSinglePage = cover + filler + license;
        DocumentContent doc = DocumentContent.fromText("bid.docx", "hash_1352", hugeSinglePage);

        QualityCheckItemResult result = DeterministicCheckPolicy.checkBusinessLicense(doc);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(result.evidences()).isNotEmpty();
        String quote = result.evidences().get(0).quote();
        assertThat(quote)
                .as("证据摘录应围绕命中关键词（营业执照），而非整页开头")
                .contains("营业执照");
        assertThat(quote)
                .as("证据摘录不应再是封面页首文本")
                .doesNotContain("张家口银行");
    }

    @Test
    @DisplayName("证据定位：命中路径 quote 非空且为合法摘录（生产 call site 词表一致，恒走命中路径）")
    void evidenceQuote_quoteNotBlank_onKeywordHit() {
        String text = "投标函\n致：张家口银行股份有限公司\n我方承诺……".repeat(200);
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash", text);

        QualityCheckItemResult result = DeterministicCheckPolicy.checkBidLetterFormat(doc);

        assertThat(result.evidences()).isNotEmpty();
        assertThat(result.evidences().get(0).quote()).isNotBlank();
    }

    @Test
    @DisplayName("投标函核查：识别谈判采购《报价函》与（采购人名称）：称谓，判定通过")
    void checkBidLetterFormat_quotationLetterWithBuyerSalutation_passes() {
        String text = """
                文件二  报价函
                中国雅江集团物资设备有限公司   （采购人名称）：
                我方已仔细研究了中国雅江集团电子商城工业用品平台供应商引入采购文件的全部内容，愿意以折扣交付货物。
                我方承诺在采购文件规定的响应文件有效期内不修改、撤销响应文件。
                如我方成交：我方承诺在收到成交通知书后30天内与采购人签订合同。
                供应商：西域智慧供应链（上海）股份公司（盖单位章）
                """;
        DocumentContent doc = DocumentContent.fromText("bid.docx", "hash_quotation", text);

        QualityCheckItemResult result = DeterministicCheckPolicy.checkBidLetterFormat(doc);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(result.conclusion()).contains("报价函格式规范");
        assertThat(result.evidences()).isNotEmpty();
        assertThat(result.evidences().get(0).sourceTitle()).isEqualTo("报价函章节");
        assertThat(result.evidences().get(0).quote()).contains("报价函");
    }

    @Test
    @DisplayName("投标函核查：第1页目录含报价函但无称谓，正文第2页含完整报价函，择优选取第2页正文")
    void checkBidLetterFormat_tableOfContentsFollowedByQuotationLetter_picksBodyPage() {
        String page1Toc = """
                目录
                一、 文件一  保密承诺书 1
                二、 文件二  报价函 3
                三、 文件三  授权委托书 4
                """;
        String page2Body = """
                文件二  报价函
                中国雅江集团物资设备有限公司 （采购人名称）：
                我方已仔细研究了采购文件全部内容。
                我方承诺在响应文件有效期内不修改、撤销响应文件。
                """;
        DocumentContent doc = DocumentContent.fromText("bid.docx", "hash_toc", page1Toc + "\f" + page2Body);

        QualityCheckItemResult result = DeterministicCheckPolicy.checkBidLetterFormat(doc);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(result.evidences()).isNotEmpty();
        assertThat(result.evidences().get(0).pageNumber()).isEqualTo(2);
        assertThat(result.evidences().get(0).quote()).contains("中国雅江集团物资设备有限公司");
    }

    @Test
    @DisplayName("投标函核查：识别《谈判响应函》与致称谓，判定通过且证据标题为响应函章节")
    void checkBidLetterFormat_negotiationResponseLetter_passes() {
        String text = """
                谈判响应函
                致：某央企物资设备有限公司
                我方仔细研究了谈判文件的全部规定，我方承诺……
                """;
        DocumentContent doc = DocumentContent.fromText("bid.docx", "hash_resp", text);

        QualityCheckItemResult result = DeterministicCheckPolicy.checkBidLetterFormat(doc);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(result.evidences().get(0).sourceTitle()).isEqualTo("响应函章节");
    }

    @Test
    @DisplayName("投标函核查：存在报价函但缺失标准称谓抬头，转入人工复核")
    void checkBidLetterFormat_quotationLetterWithoutSalutation_returnsManualReview() {
        String text = """
                报价函
                我方承诺在响应文件有效期内不修改、撤销响应文件。
                """;
        DocumentContent doc = DocumentContent.fromText("bid.docx", "hash_manual", text);

        QualityCheckItemResult result = DeterministicCheckPolicy.checkBidLetterFormat(doc);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.suggestion()).contains("抬头致招标人或采购人称谓是否准确");
    }

    @Test
    @DisplayName("投标函核查：全文未检索到任何投标函/报价函/响应函，判定失败")
    void checkBidLetterFormat_noBidLetter_fails() {
        String text = "这里仅包含普通技术规范描述与人员简历，无任何函件章节。";
        DocumentContent doc = DocumentContent.fromText("bid.docx", "hash_fail", text);

        QualityCheckItemResult result = DeterministicCheckPolicy.checkBidLetterFormat(doc);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.conclusion()).contains("未检索到投标函或报价函独立章节");
    }

    @Test
    @DisplayName("snippetAround 纯函数：关键词未命中时回退页首摘录（兜底分支锚定）")
    void snippetAround_fallsBackToHeadSnippet_whenNoKeywordHit() {
        String text = "这段文本完全不含任何检索关键词，用于验证兜底路径。".repeat(50);

        String fallback = BasicInfoCheckPolicy.snippetAround(text, 30, "不存在的词甲", "不存在的词乙");
        String head = BasicInfoCheckPolicy.snippet(text, 30);

        assertThat(fallback)
                .as("未命中任何关键词时行为应与页首 snippet 一致")
                .isEqualTo(head);
    }

    @Test
    @DisplayName("snippetAround 纯函数：命中时窗口内含关键词，且为原文子串（空白压缩前）")
    void snippetAround_hit_returnsWindowAroundKeyword() {
        String prefix = "开头填充。".repeat(40);
        String suffix = "结尾填充。".repeat(40);
        String text = prefix + "营业执照编号九一三一零零零零零零" + suffix;

        String quote = BasicInfoCheckPolicy.snippetAround(text, 40, "营业执照");

        assertThat(quote).contains("营业执照");
        // 窗口取自原文：quote 首部字符应在原文（去空白）中重现，而非凭空拼缀
        String headChars = quote.replace("...", "").replaceAll("\\s+", "").substring(0, 5);
        assertThat(text.replaceAll("\\s+", "")).contains(headChars);
    }

    @Test
    @DisplayName("开标一览表：文档使用「投标报价表」表述时也能识别（词表扩充）")
    void bidOpeningTable_recognizesBidPriceTable() {
        String page = "二、投标报价表\n投标总价：大写：人民币壹佰万元整 小写：¥1000000.00\n分项报价明细见附录";
        DocumentContent doc = DocumentContent.fromText("bid.docx", "hash_1352", page);

        QualityCheckItemResult result = DeterministicCheckPolicy.checkBidOpeningTable(doc);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(result.evidences()).isNotEmpty();
        assertThat(result.evidences().get(0).quote()).contains("投标报价表");
    }

    @Test
    @DisplayName("报价一致性：大写与小写分处两页时经全文回退仍能比对（跨页报价对）")
    void priceConsistency_crossPagePair_fallsBackToFullText() {
        // 页 1 只有投标函大写总价；页 2 只有报价表小写金额——逐页匹配必然失败
        String page1 = "投标函\n投标总价 大写：人民币壹佰万元整\n特此承诺。";
        String page2 = "开标一览表\n报价总额：¥1000000.00\n小写：1000000.00";
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash", page1 + "\f" + page2);

        QualityCheckItemResult result = DeterministicCheckPolicy.checkPriceConsistency(doc);

        assertThat(result.status())
                .as("跨页大小写对照应经全文回退命中并判定一致")
                .isEqualTo(QualityCheckStatus.PASS);
    }

    @Test
    @DisplayName("报价一致性：大写与小写距离超窗时不得远距误配，保持转人工")
    void priceConsistency_oversizedCrossLineGap_doesNotMismatch() {
        // 大写与小写之间隔 > 300 字符（跨行窗口外），不得强行配对
        String page1 = "投标总价 大写：人民币壹佰万元整\n" + "无关填充内容，用于拉开大小写距离。".repeat(30);
        String page2 = "另一处出现 小写：1000000.00 与本节无关";
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash", page1 + "\f" + page2);

        QualityCheckItemResult result = DeterministicCheckPolicy.checkPriceConsistency(doc);

        assertThat(result.status())
                .as("超窗的远距大小写对照不应被判为一致")
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    @DisplayName("报价一致性：「大写：」后无金额时不得折算为 0 误判 FAIL，应转人工")
    void priceConsistency_emptyChineseAmount_fallsBackToManualReview() {
        // "大写：" 后紧跟无金额内容，300 字符窗口内出现别处的"小写：100"——不得配成 ¥0 的假 FAIL
        String page1 = "投标报价说明\n大写：\n（金额见正式报价页）\n" + "填充内容。".repeat(10);
        String page2 = "分项报价\n小写：100\n（分项单价，非总价）";
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash", page1 + "\f" + page2);

        QualityCheckItemResult result = DeterministicCheckPolicy.checkPriceConsistency(doc);

        assertThat(result.status())
                .as("空大写金额不构成价格对，应转人工而非 FAIL 折算 ¥0")
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    @DisplayName("项目1358真实场景回归：招标文件前附表免收时，标书无论是否附回单均判定通过")
    void checkDepositReceipt_project1358TableExemption_passes() {
        String tenderText = """
                | 序号 | 条款名称 | 编列内容 |
                | 7.1 | 投标保证金 | 不适用 |
                """;
        String bidText = "目录\n附件一 投标函\n附件七 投标保证金汇款凭证 ...... 18";
        DocumentContent bidDoc = DocumentContent.fromText("bid.pdf", "hash_bid", bidText);

        QualityCheckItemResult result = SubItemAndAttachmentRules.checkDepositReceipt(bidDoc, tenderText);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(result.conclusion()).contains("免收/不涉及");
    }

    @Test
    @DisplayName("防假通过反例1：招标要求缴纳时，标书目录点线超前命中坚决判定未附凭证，杜绝假通过")
    void checkDepositReceipt_whenRequired_tocPageMustNotPass() {
        String tenderText = "投标保证金：人民币50000元，截标前电汇至指定账户。";
        // 标书第 1 页仅为目录点线结构，第 2 页为正文，未附任何真实回单
        String page1Toc = "目录\n第一章 投标函 ...... 1\n第七章 投标保证金汇款回单 ...... 25\n第八章 授权委托书 ...... 26";
        String page2Body = "投标正文：我方对本项目深表重视。";
        DocumentContent bidDoc = DocumentContent.fromText("bid.pdf", "hash_bid", page1Toc + "\f" + page2Body);

        QualityCheckItemResult result = SubItemAndAttachmentRules.checkDepositReceipt(bidDoc, tenderText);

        assertThat(result.status())
                .as("标书仅有目录条目时，坚决不能误判通过")
                .isNotEqualTo(QualityCheckStatus.PASS);
    }

    @Test
    @DisplayName("防假通过反例2：招标要求缴纳时，标书正文单方口头声明已缴纳但无回单，坚决判定未附凭证")
    void checkDepositReceipt_whenRequired_oralDeclarationMustNotPass() {
        String tenderText = "3.4 投标担保：本项目要求提交投标担保，金额为人民币50,000元。";
        String bidText = "我方声明：我方已按照招标文件规定足额缴纳了投标保证金与投标担保，特此声明。";
        DocumentContent bidDoc = DocumentContent.fromText("bid.pdf", "hash_bid", bidText);

        QualityCheckItemResult result = SubItemAndAttachmentRules.checkDepositReceipt(bidDoc, tenderText);

        assertThat(result.status())
                .as("纯口头声明无凭证事实要素时，绝不能判定通过")
                .isNotEqualTo(QualityCheckStatus.PASS);
    }

    @Test
    @DisplayName("正向凭证要素测试：标书包含真实银行电子回单（凭证类型+交易流水号+收款人），判定通过")
    void checkDepositReceipt_whenRequired_actualVoucherPasses() {
        String tenderText = "投标保证金金额：50000元。";
        String bidVoucherPage = """
                中国工商银行电子回单
                业务流水号：2026090510293847
                交易类型：跨行转账汇款   转账成功
                付款人户名：西域智慧供应链（上海）股份公司
                收款人户名：某某项目招标代理中心
                转账金额：人民币伍万元整（¥50,000.00）
                回单专用章：[电子印章 91310000]
                """;
        DocumentContent bidDoc = DocumentContent.fromText("bid.pdf", "hash_bid", bidVoucherPage);

        QualityCheckItemResult result = SubItemAndAttachmentRules.checkDepositReceipt(bidDoc, tenderText);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(result.conclusion()).contains("已附投标保证金汇款回单或电子保函凭证");
        assertThat(result.evidences()).isNotEmpty();
    }

    @Test
    @DisplayName("防假通过反例3（深度压测）：标书命中强关键词（银行汇款回单/投标保证金汇款）但无流水号/印章等事实要素，经 isActualDepositVoucherPage 拦截，转人工核实绝不放行通过")
    void checkDepositReceipt_whenRequired_hitKeywordsWithoutVoucherFacts_mustNotPass() {
        String tenderText = "投标保证金：人民币50000元。";
        // 标书正文包含强关键词“投标保证金汇款”与“银行汇款回单”，能成功命中 strongMatch 并进入 isActualDepositVoucherPage 严格核查，
        // 但正文仅为口头声明与说明，缺乏流水号、交易号、转账成功、电子印章等真实交易数据要素
        String bidText = """
                关于投标保证金汇款回单的说明
                致招标人：
                我单位已按照招标文件要求足额办理了投标保证金汇款，附上银行汇款回单相关说明。
                我方郑重承诺已按期缴存，特此声明。
                投标人：西域智慧供应链（上海）股份公司
                """;
        DocumentContent bidDoc = DocumentContent.fromText("bid.pdf", "hash_bid", bidText);

        QualityCheckItemResult result = SubItemAndAttachmentRules.checkDepositReceipt(bidDoc, tenderText);

        assertThat(result.status())
                .as("命中强关键词但无真实交易要素（无流水号/印章）时，由 isActualDepositVoucherPage 拦截，绝不误判为 PASS")
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.conclusion())
                .contains("无法确认为实际凭证");
    }
}
