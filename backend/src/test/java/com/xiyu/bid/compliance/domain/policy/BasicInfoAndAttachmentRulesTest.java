package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BasicInfoAndAttachmentRulesTest {

    @Test
    @DisplayName("基本信息签章核验：集成SealIntegrityCheckPolicy在真实公章与法人签章时返回PASS")
    void testBasicInfoCheckSignatureSeal_WhenSealed_Passes() {
        String bidText = """
                投标函
                投标人名称：西域供应链（上海）有限公司
                法定代表人：张三（签字）
                投标人：西域供应链（上海）有限公司（盖单位公章）
                日期：2026年09月06日
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "h1", bidText);

        QualityCheckItemResult result = BasicInfoCheckPolicy.checkSignatureSeal(doc);

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(result.conclusion()).contains("符合签章规范");
    }

    @Test
    @DisplayName("基本信息签章反假：仅有未替换下划线占位符时直接判定FAIL")
    void testBasicInfoCheckSignatureSeal_WhenUnfilledPlaceholder_Fails() {
        String bidText = """
                投标函
                法定代表人：______
                投标人：______
                日期：2026年__月__日
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "h2", bidText);

        QualityCheckItemResult result = BasicInfoCheckPolicy.checkSignatureSeal(doc);

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.conclusion()).contains("占位符");
    }

    @Test
    @DisplayName("承诺书签章核验：承诺书包含真实公章与签署时返回PASS")
    void testCommitmentSeal_WhenSignedAndSealed_Passes() {
        String bidText = """
                廉洁投标承诺书
                我单位郑重承诺在本次投标活动中严格遵守法律法规，绝无行贿受贿行为。
                承诺人：西域供应链（上海）有限公司（盖章）
                法定代表人：李四（签字）
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "h3", bidText);

        QualityCheckItemResult result = SubItemAndAttachmentRules.checkCommitmentSeal(doc);

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(result.conclusion()).contains("包含合规承诺书且签章要素齐全");
    }

    @Test
    @DisplayName("承诺书反假：承诺书保留未替换提示占位符时判定FAIL")
    void testCommitmentSeal_WhenTemplatePlaceholder_Fails() {
        String bidText = """
                诚信投标承诺书
                我公司郑重承诺无重大违法记录。
                承诺单位：西域供应链（上海）有限公司
                【此处盖章】
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "h4", bidText);

        QualityCheckItemResult result = SubItemAndAttachmentRules.checkCommitmentSeal(doc);

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.conclusion()).contains("空白占位或未盖章模板标记");
    }

    @Test
    @DisplayName("承诺书人工复核：检索到承诺书但缺少明确签章标记时返回MANUAL_REVIEW")
    void testCommitmentSeal_WhenNoSealMarks_RequiresManualReview() {
        String bidText = """
                服务质量承诺书
                西域供应链郑重承诺严格保障供应链履约质量。
                特此声明。
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "h5", bidText);

        QualityCheckItemResult result = SubItemAndAttachmentRules.checkCommitmentSeal(doc);

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.conclusion()).contains("未识别到明确的公章或签字标记");
    }

    @Test
    @DisplayName("承诺书反假：落款仅有格式说明文字且未填签署人时降级人工复核而非PASS")
    void testCommitmentSeal_WhenInstructionOnlyText_RequiresManualReview() {
        String bidText = """
                廉洁投标承诺书（模板）
                本单位郑重承诺遵守招投标法律法规。
                投标人（盖章）：
                法定代表人（签字）：
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "h6", bidText);

        QualityCheckItemResult result = SubItemAndAttachmentRules.checkCommitmentSeal(doc);

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.conclusion()).contains("文本层无法确认实际盖章");
    }
}
