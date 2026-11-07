package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

class RuleCitationContextTest {
    @ParameterizedTest
    @ValueSource(strings = {
        "我方承诺不存在以下情形：\n1. 本次投标已弄虚作假提交材料。",
        "我方承诺不存在以下情形：1. 本次投标已弄虚作假提交材料。",
        "我方承诺不存在以下情形：\n1. 我司已进行违法分包。",
        "本次投标已弄虚作假提交材料。"})
    void directOwnFactCannotBeExemptedByListHeader(String text) {
        assertThat(SubItemAndAttachmentRules.checkVetoKeyword(
                DocumentContent.fromText("bid.docx", "h", text)).status()).isEqualTo(QualityCheckStatus.FAIL);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "我方承诺不存在以下情形：\n1. 弄虚作假提交材料。",
        "我方承诺不存在以下情形：\n1. 本次投标未发生弄虚作假。",
        "若投标人弄虚作假，将由评标委员会予以否决。"})
    void ordinaryDenialAndWarningRemainExempt(String text) {
        assertThat(SubItemAndAttachmentRules.checkVetoKeyword(
                DocumentContent.fromText("bid.docx", "h", text)).status()).isEqualTo(QualityCheckStatus.PASS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"我方承诺不存在以下情形：\n1. 本次投标若发生弄虚作假，将追究责任。",
        "我方承诺不存在以下情形：\n1. 弄虚作假：我司相关情况待核实，未按要求说明",
        "我方承诺不存在以下情形：\n1. 弄虚作假：我司相关不实材料情况待核实。",
        "我方承诺不存在以下情形：\n1. 弄虚作假：我司已提交不实材料。",
        "我方承诺不存在以下情形：\n1. 弄虚作假：我司已提交不实材料",
        "我方承诺不存在以下情形：\n1. 弄虚作假：我司已进行材料伪造并提交，未按要求更正。",
        "我方承诺不存在以下情形：\n1. 弄虚作假：我司已进行材料伪造并提交，未按要求更正",
        "我方承诺不存在以下情形：\n1. 弄虚作假：我司已进行材料伪造并提交；未按要求更正。",
        "我方承诺不存在以下情形：\n1. 弄虚作假：我司已进行材料伪造并提交。未按要求更正。",
        "我方承诺不存在以下情形：\n1. 弄虚作假：我司未提交不实材料。",
        "我方承诺不存在以下情形：\n1. 弄虚作假：我司已不存在材料伪造情形。",
        "我方承诺不存在以下情形：\n1. 弄虚作假：我司未进行材料伪造。"})
    void unresolvedOwnContextRequiresConfirmation(String text) {
        assertThat(SubItemAndAttachmentRules.checkVetoKeyword(
                DocumentContent.fromText("bid.docx", "h", text)).status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }
}
