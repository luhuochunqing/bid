package com.xiyu.bid.tender.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CO-501 第二步本地一致性校验单测。
 */
class TenderSubjectConsistencyPolicyTest {

    @Test
    @DisplayName("标讯招标主体为 null → 直接允许关联")
    void check_whenPurchaserNameIsNull_shouldAllow() {
        TenderSubjectConsistencyPolicy.Result result = TenderSubjectConsistencyPolicy.check(
                null, "山东海化集团", "山东海化集团有限公司");

        assertThat(result.allowed()).isTrue();
        assertThat(result.errorMessage()).isNull();
    }

    @Test
    @DisplayName("标讯招标主体为空白 → 直接允许关联")
    void check_whenPurchaserNameIsBlank_shouldAllow() {
        TenderSubjectConsistencyPolicy.Result result = TenderSubjectConsistencyPolicy.check(
                "   ", "山东海化集团", "山东海化集团有限公司");

        assertThat(result.allowed()).isTrue();
    }

    @Test
    @DisplayName("标讯招标主体等于商机集团名称 → 允许")
    void check_whenPurchaserMatchesGroupName_shouldAllow() {
        TenderSubjectConsistencyPolicy.Result result = TenderSubjectConsistencyPolicy.check(
                "山东海化集团", "山东海化集团", "山东海化集团有限公司");

        assertThat(result.allowed()).isTrue();
    }

    @Test
    @DisplayName("标讯招标主体等于商机招标主体名称 → 允许")
    void check_whenPurchaserMatchesTenderSubject_shouldAllow() {
        TenderSubjectConsistencyPolicy.Result result = TenderSubjectConsistencyPolicy.check(
                "山东海化集团有限公司", "山东海化集团", "山东海化集团有限公司");

        assertThat(result.allowed()).isTrue();
    }

    @Test
    @DisplayName("标讯招标主体与商机 groupName/tenderSubject 都不一致 → 拒绝")
    void check_whenPurchaserMatchesNeither_shouldReject() {
        TenderSubjectConsistencyPolicy.Result result = TenderSubjectConsistencyPolicy.check(
                "中石化集团", "山东海化集团", "山东海化集团有限公司");

        assertThat(result.allowed()).isFalse();
        assertThat(result.errorMessage()).isEqualTo("招标主体不一致，请到 CRM 中修改");
    }

    @Test
    @DisplayName("商机 groupName 与 tenderSubject 都为 null → 不匹配时拒绝")
    void check_whenChanceFieldsAreNull_shouldRejectIfNoMatch() {
        TenderSubjectConsistencyPolicy.Result result = TenderSubjectConsistencyPolicy.check(
                "山东海化集团", null, null);

        assertThat(result.allowed()).isFalse();
        assertThat(result.errorMessage()).isEqualTo("招标主体不一致，请到 CRM 中修改");
    }

    @Test
    @DisplayName("商机 groupName 与 tenderSubject 都为空字符串 → 不匹配时拒绝")
    void check_whenChanceFieldsAreEmpty_shouldRejectIfNoMatch() {
        TenderSubjectConsistencyPolicy.Result result = TenderSubjectConsistencyPolicy.check(
                "山东海化集团", "", "");

        assertThat(result.allowed()).isFalse();
    }
}
