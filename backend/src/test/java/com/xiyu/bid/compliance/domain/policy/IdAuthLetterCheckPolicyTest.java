package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IdAuthLetterCheckPolicyTest {

    @Test
    @DisplayName("提供有效身份证扫描件与18位公民身份号码时判定PASS")
    void check_WhenValidIdCardProvided_Pass() {
        var pages = List.of(
                new DocumentContent.PageContent(1, "投标须知及目录概要...", List.of()),
                new DocumentContent.PageContent(6, """
                        附件三：法定代表人及委托代理人身份证明
                        委托代理人姓名：李四
                        性别：男    民族：汉
                        出生：1990年1月1日
                        住址：北京市海淀区中关村南大街1号
                        公民身份号码：110108199001012345
                        中华人民共和国居民身份证
                        签发机关：北京市公安局海淀分局
                        有效期限：2020.01.01-2040.01.01
                        （身份证扫描件正反面已加盖单位公章）
                        """, List.of())
        );
        var bidDoc = new DocumentContent("标书.pdf", "hash_id", "", pages);
        var req = new IdAuthLetterRequirement(true, true, false, "复印件加盖公章", "需提供身份证复印件");

        QualityCheckItemResult result = IdAuthLetterCheckPolicy.check(bidDoc, req);

        assertThat(result.itemCode()).isEqualTo(BidQualityCheckItem.ID_AUTH_LETTER.getCode());
        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(result.conclusion()).contains("已包含有效身份证明与身份证扫描件");
        assertThat(result.conclusion()).contains("110108********2345");
        assertThat(result.evidences()).isNotEmpty();
        assertThat(result.evidences().get(0).pageNumber()).isEqualTo(6);
    }

    @Test
    @DisplayName("仅在目录页出现条目且包含点线引导符时拦截，转人工复核")
    void check_WhenTableOfContentsOnly_ReturnsManualReview() {
        var pages = List.of(
                new DocumentContent.PageContent(1, """
                        目    录
                        一、投标函及投标函附录 ................................. 1
                        二、法定代表人身份证明及授权委托书 ..................... 3
                        三、委托代理人身份证复印件及社保证明 ................... 6
                        四、营业执照及资质证书 ................................. 7
                        """, List.of())
        );
        var bidDoc = new DocumentContent("标书.pdf", "hash_toc", "", pages);

        QualityCheckItemResult result = IdAuthLetterCheckPolicy.check(bidDoc);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.conclusion()).contains("仅在目录页检测到");
    }

    @Test
    @DisplayName("仅包含未替换的占位符模板时拦截转人工复核")
    void check_WhenUnfilledTemplate_ReturnsManualReview() {
        var pages = List.of(
                new DocumentContent.PageContent(5, """
                        附件：授权代表身份证明
                        【此处粘贴委托代理人身份证正面扫描件】
                        【此处粘贴委托代理人身份证反面扫描件】
                        委托代理人身份证号码：____________________
                        联系电话：[待填写]
                        """, List.of())
        );
        var bidDoc = new DocumentContent("标书.pdf", "hash_tpl", "", pages);

        QualityCheckItemResult result = IdAuthLetterCheckPolicy.check(bidDoc);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.conclusion()).contains("占位符模板");
        assertThat(result.evidences()).isNotEmpty();
        assertThat(result.evidences().get(0).pageNumber()).isEqualTo(5);
    }

    @Test
    @DisplayName("招标明确要求社保证明且标书已提供社保证明时判定PASS")
    void check_WhenSocialSecurityRequiredAndProvided_Pass() {
        var pages = List.of(
                new DocumentContent.PageContent(6, """
                        委托代理人身份证正反面扫描件
                        姓名：张三
                        公民身份号码：310115198808081234
                        中华人民共和国居民身份证
                        签发机关：上海市公安局浦东分局
                        """, List.of()),
                new DocumentContent.PageContent(7, """
                        上海市城镇职工基本养老保险参保证明
                        参保单位：上海西域数智供应链有限公司
                        参保人员：张三
                        社会保险缴纳记录：近3个月依法全额缴纳，缴费状态正常。
                        """, List.of())
        );
        var bidDoc = new DocumentContent("标书.pdf", "hash_ss", "", pages);
        var req = new IdAuthLetterRequirement(true, true, true, "原件或复印件", "必须提供身份证及社保证明");

        QualityCheckItemResult result = IdAuthLetterCheckPolicy.check(bidDoc, req);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(result.conclusion()).contains("已包含有效身份证明与身份证扫描件");
    }

    @Test
    @DisplayName("招标明确要求社保证明但标书未附社保证明时转人工复核并告警")
    void check_WhenSocialSecurityRequiredButMissing_ReturnsManualReview() {
        var pages = List.of(
                new DocumentContent.PageContent(6, """
                        委托代理人身份证正反面扫描件
                        姓名：张三
                        公民身份号码：310115198808081234
                        中华人民共和国居民身份证
                        签发机关：上海市公安局浦东分局
                        """, List.of())
        );
        var bidDoc = new DocumentContent("标书.pdf", "hash_no_ss", "", pages);
        var req = new IdAuthLetterRequirement(true, true, true, "原件或复印件", "必须提供委托代理人社保证明");

        QualityCheckItemResult result = IdAuthLetterCheckPolicy.check(bidDoc, req);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.conclusion()).contains("未检测到招标要求的委托代理人社保证明");
    }

    @Test
    @DisplayName("标书中完全缺失身份证明与身份证件时判定FAIL")
    void check_WhenCompletelyMissing_ReturnsFail() {
        var pages = List.of(
                new DocumentContent.PageContent(1, "第一章 投标邀请", List.of()),
                new DocumentContent.PageContent(2, "第二章 投标报价表", List.of())
        );
        var bidDoc = new DocumentContent("标书.pdf", "hash_none", "", pages);

        QualityCheckItemResult result = IdAuthLetterCheckPolicy.check(bidDoc);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.conclusion()).contains("未检索到法定代表人或委托代理人身份证件证明");
    }

    @Test
    @DisplayName("身份证页包含已附图片占位或图片标记时判定PASS")
    void check_WhenImageEvidenceProvided_Pass() {
        var pages = List.of(
                new DocumentContent.PageContent(5, """
                        附件三：法定代表人及委托代理人身份证明
                        | 身份证（人像页） | 身份证（国徽页） |
                        | 已附图片 | 已附图片 |
                        委托代理人姓名：李四
                        """, List.of())
        );
        var bidDoc = new DocumentContent("标书.docx", "hash_img_id", "", pages);

        QualityCheckItemResult result = IdAuthLetterCheckPolicy.check(bidDoc);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(result.conclusion()).contains("已包含有效身份证明并附带身份证扫描件/复印件图片凭证");
        assertThat(result.evidences()).isNotEmpty();
        assertThat(result.evidences().get(0).pageNumber()).isEqualTo(5);
    }
}
