package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 投标函格式规范 (BID_LETTER_FORMAT) 防假与事实要素校验测试套件.
 */
class BidLetterCheckPolicyTest {

    @Test
    @DisplayName("防假测试：标书仅在目录页列出投标函点线，正文无实质章节，断言FAIL并明确排除目录索引")
    void checkBidLetterFormat_tableOfContentsOnly_fails() {
        String tocPage = """
                目录
                一、投标函及投标函附录 ................................. 1
                二、法定代表人身份证明 ................................. 5
                三、授权委托书 ......................................... 6
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash_toc", tocPage);

        QualityCheckItemResult result = BasicInfoCheckPolicy.checkBidLetterFormat(doc);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.conclusion()).contains("未检索到投标函或报价函独立章节");
        assertThat(result.conclusion()).contains("已排除目录索引");
    }

    @Test
    @DisplayName("防假测试：标书正文包含未填写的占位符模板（如【采购人名称】或下划线留白），断言转人工复核")
    void checkBidLetterFormat_unfilledPlaceholderTemplate_returnsManualReview() {
        String templatePage = """
                投标函
                致：【招标人名称】：
                我方已仔细研究了招标文件的全部内容，愿以投标总价人民币：__________元 承担项目。
                我方承诺投标有效期为90日历天。
                投标人：（投标人名称）（盖章）
                法定代表人：（签字或盖章）
                日期：2026年  月  日
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash_template", templatePage);

        QualityCheckItemResult result = BasicInfoCheckPolicy.checkBidLetterFormat(doc);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.conclusion()).contains("存在未填写的占位符模板或下划线");
        assertThat(result.suggestion()).contains("请确认投标函中采购人称谓、投标总价或落款信息是否已完整替换填写");
    }

    @Test
    @DisplayName("招标端契约核验：标书承诺有效期（60天）低于招标文件要求（90天），断言FAIL")
    void checkBidLetterFormat_insufficientValidityDays_fails() {
        String bidPage = """
                投标函
                致：上海西域机电系统有限公司：
                我方已仔细研究了招标文件的全部内容，愿以合理价格履约。
                我方承诺：投标有效期为自开标之日起60日历天。
                投标人：某某科技发展有限公司（盖章）
                法定代表人：张三
                日期：2026年09月01日
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash_bid", bidPage);
        TenderRequirements.BidLetterRequirement req = new TenderRequirements.BidLetterRequirement(
                "投标函", "上海西域机电系统有限公司", 90, "投标有效期90天");

        QualityCheckItemResult result = BasicInfoCheckPolicy.checkBidLetterFormat(doc, req);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.conclusion()).contains("投标函承诺的投标有效期（60天）少于招标文件要求的（90天）");
    }

    @Test
    @DisplayName("招标端契约核验：标书致采购人称谓与招标文件指定称谓不一致，断言转人工复核")
    void checkBidLetterFormat_mismatchedRecipientName_returnsManualReview() {
        String bidPage = """
                投标函
                致：北京某某集团采购中心：
                我方已仔细研究了招标文件的全部内容，我方承诺响应所有条款。
                本投标函有效期为90天。
                投标人：某某科技发展有限公司（盖章）
                法定代表人：李四
                日期：2026年09月01日
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash_bid", bidPage);
        TenderRequirements.BidLetterRequirement req = new TenderRequirements.BidLetterRequirement(
                "投标函", "上海西域机电系统有限公司", 90, "致上海西域机电系统有限公司");

        QualityCheckItemResult result = BasicInfoCheckPolicy.checkBidLetterFormat(doc, req);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.conclusion()).contains("投标函致采购人称谓与招标文件要求的【上海西域机电系统有限公司】不一致");
    }

    @Test
    @DisplayName("正向要素完备正例：具备规范称谓、正文承诺、落款签章且符合要求，断言PASS并直出强凭证")
    void checkBidLetterFormat_fullElementsCompliant_passes() {
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
        TenderRequirements.BidLetterRequirement req = new TenderRequirements.BidLetterRequirement(
                "报价函", "上海西域机电系统有限公司", 90, "格式为报价函，有效期90天");

        QualityCheckItemResult result = BasicInfoCheckPolicy.checkBidLetterFormat(doc, req);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(result.conclusion()).contains("投标函/报价函格式规范，要素完备");
        assertThat(result.evidences()).isNotEmpty();
        assertThat(result.evidences().get(0).sourceTitle()).isEqualTo("报价函章节");
        assertThat(result.evidences().get(0).quote()).contains("上海西域机电系统有限公司");
    }
}
