package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ClarificationResponsePolicyTest {

    @Test
    @DisplayName("澄清响应：招标文件无澄清答疑要求且标书未提及澄清时直通 PASS")
    void testCheckFirstStage_WhenTenderHasNoClarification_Passes() {
        DocumentContent tenderDoc = DocumentContent.fromText("tender.pdf", "thash1", "常规招标文件，技术规范与商务条款");
        DocumentContent bidDoc = DocumentContent.fromText("bid.pdf", "bhash1", "标准投标文件技术方案正文");

        Optional<QualityCheckItemResult> result = ClarificationResponsePolicy.checkFirstStage(
                bidDoc, tenderDoc, TenderRequirements.empty());

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(result.get().conclusion()).contains("无需单独响应");
    }

    @Test
    @DisplayName("澄清响应：仅在目录中列出澄清或答疑确认条目判定 FAIL")
    void testCheckFirstStage_WhenOnlyInCatalog_Fails() {
        DocumentContent tenderDoc = DocumentContent.fromText("tender.pdf", "thash2", "请各投标人按第1号澄清文件修改响应");
        String text = """
                目 录
                第五章 澄清答疑与补遗文件确认函.......................... 56
                """;
        DocumentContent bidDoc = DocumentContent.fromText("bid.pdf", "bhash2", text);

        Optional<QualityCheckItemResult> result = ClarificationResponsePolicy.checkFirstStage(
                bidDoc, tenderDoc, TenderRequirements.empty());

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.get().conclusion()).contains("仅在目录中");
    }

    @Test
    @DisplayName("澄清响应：澄清确认函中存在模板占位留白未填时判定 FAIL")
    void testCheckFirstStage_WhenPlaceholderPresent_Fails() {
        DocumentContent tenderDoc = DocumentContent.fromText("tender.pdf", "thash3", "发布第1号澄清文件");
        DocumentContent bidDoc = new DocumentContent("bid.pdf", "bhash3", "full", List.of(
                new DocumentContent.PageContent(1, "目 录\n澄清答疑确认函...... 1", List.of()),
                new DocumentContent.PageContent(2, """
                        关于招标文件的澄清确认函
                        致采购人：
                        我司已确认收到第【填写澄清编号】号澄清文件，并全面遵守相关答疑要求。
                        投标人盖章：___________
                        日期：___________
                        """, List.of())
        ));

        Optional<QualityCheckItemResult> result = ClarificationResponsePolicy.checkFirstStage(
                bidDoc, tenderDoc, TenderRequirements.empty());

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.get().conclusion()).contains("占位符或下划线留白");
    }

    @Test
    @DisplayName("澄清响应：标书包含真实完备签署的澄清确认函时直通 PASS")
    void testCheckFirstStage_WhenValidConfirmationPresent_Passes() {
        DocumentContent tenderDoc = DocumentContent.fromText("tender.pdf", "thash4", "招标文件发布第1号澄清文件");
        DocumentContent bidDoc = new DocumentContent("bid.pdf", "bhash4", "full", List.of(
                new DocumentContent.PageContent(1, "目 录\n澄清答疑确认函...... 1", List.of()),
                new DocumentContent.PageContent(2, """
                        关于招标文件第 1 号澄清答疑文件的确认函
                        致招标人：
                        我方已于2026年9月1日收悉贵方发布的第1号答疑纪要与补遗文件，对其中关于交货期与参数调整内容已悉知并遵守，相关要求已全部吸收进本次投标文件响应中。
                        投标人：西域数字科技有限公司（盖章）
                        法定代表人或授权代表：张三（签署）
                        日期：2026年9月5日
                        """, List.of())
        ));

        Optional<QualityCheckItemResult> result = ClarificationResponsePolicy.checkFirstStage(
                bidDoc, tenderDoc, TenderRequirements.empty());

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(result.get().conclusion()).contains("实质性确认并响应答疑事项");
    }

    @Test
    @DisplayName("澄清响应：招标发布了澄清但标书未附独立确认函时移交 LLM 语义裁决")
    void testCheckFirstStage_WhenTenderRequiresClarificationAndBidHasNoLetter_HandsOverToLlm() {
        DocumentContent tenderDoc = DocumentContent.fromText("tender.pdf", "thash5", "招标文件发布第1号答疑补遗文件，修改技术指标");
        DocumentContent bidDoc = DocumentContent.fromText("bid.pdf", "bhash5", "投标文件技术方案正文，包含微服务架构与交付");

        Optional<QualityCheckItemResult> result = ClarificationResponsePolicy.checkFirstStage(
                bidDoc, tenderDoc, TenderRequirements.empty());

        // 移交 LLM 判断技术方案是否实际吸收了答疑修改
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("澄清响应：文档为空时移交 LLM 语义裁决")
    void testCheckFirstStage_WhenDocEmpty_ReturnsEmpty() {
        assertThat(ClarificationResponsePolicy.checkFirstStage(null, null, TenderRequirements.empty())).isEmpty();
    }
}
