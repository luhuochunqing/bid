package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SealIntegrityCheckPolicyTest {

    @Test
    @DisplayName("具备电子签章防伪验签标记直通PASS")
    void testElectronicSealPass() {
        DocumentContent.PageContent p1 = new DocumentContent.PageContent(
                1,
                """
                投标函
                致：采购人
                我方承诺完全响应招标文件要求。
                投标人：上海西域数智供应链有限公司
                法定代表人：张三
                本文件已由第三方 CA 机构完成数字签名，电子印章验证通过，国密SM2签名防伪验签有效。
                加盖骑缝章。
                """,
                List.of()
        );
        DocumentContent doc = new DocumentContent("bid.pdf", "h1", "", List.of(p1));
        QualityCheckItemResult res = SealIntegrityCheckPolicy.check(doc);

        assertThat(res.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(res.conclusion()).contains("电子签章防伪验签标记");
        assertThat(res.conclusion()).contains("骑缝章连续性覆盖");
    }

    @Test
    @DisplayName("同时具备法定代表人签字与单位公章直通PASS")
    void testLegalRepAndCompanySealPass() {
        DocumentContent.PageContent p1 = new DocumentContent.PageContent(
                1,
                """
                投标函
                投标人：（盖单位公章）
                法定代表人（签字或盖章）：李四
                每页加盖公章或骑缝章
                """,
                List.of()
        );
        DocumentContent doc = new DocumentContent("bid.pdf", "h1", "", List.of(p1));
        QualityCheckItemResult res = SealIntegrityCheckPolicy.check(doc);

        assertThat(res.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(res.conclusion()).contains("法定代表人印章/签字与投标人单位公章");
    }

    @Test
    @DisplayName("未替换模板占位符且无真实签署直接硬FAIL")
    void testUnfilledPlaceholderFail() {
        DocumentContent.PageContent p1 = new DocumentContent.PageContent(
                1,
                """
                投标函落款
                投标人：____________________
                法定代表人：____________________
                【此处盖公章】
                """,
                List.of()
        );
        DocumentContent doc = new DocumentContent("bid.pdf", "h1", "", List.of(p1));
        QualityCheckItemResult res = SealIntegrityCheckPolicy.check(doc);

        assertThat(res.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(res.conclusion()).contains("[UNFILLED_SEAL_PLACEHOLDER]");
    }

    @Test
    @DisplayName("仅有单位公章但无法人签字降级人工复核")
    void testCompanySealOnlyManualReview() {
        DocumentContent.PageContent p1 = new DocumentContent.PageContent(
                1,
                """
                授权委托书
                投标单位（盖章）：某某软件公司
                日期：2026年9月1日
                """,
                List.of()
        );
        DocumentContent doc = new DocumentContent("bid.pdf", "h1", "", List.of(p1));
        QualityCheckItemResult res = SealIntegrityCheckPolicy.check(doc);

        assertThat(res.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(res.conclusion()).contains("已检测到单位公章落款，但未能明确识别法定代表人个人印章或签字");
    }

    @Test
    @DisplayName("仅在目录页出现签章说明正文缺失判定FAIL")
    void testCatalogDotsIgnoredAndFail() {
        DocumentContent.PageContent catalogPage = new DocumentContent.PageContent(
                1,
                """
                目 录
                一、法定代表人签字与盖章页 .................... 12
                二、技术偏离表 .................... 15
                """,
                List.of()
        );
        DocumentContent doc = new DocumentContent("bid.pdf", "h1", "", List.of(catalogPage));
        QualityCheckItemResult res = SealIntegrityCheckPolicy.check(doc);

        assertThat(res.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(res.conclusion()).contains("未在标书核心页中检测到法定代表人印章、公章或电子防伪签章");
    }
}
