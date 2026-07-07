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
    @DisplayName("商机 groupName 与 tenderSubject 都为 null → 放行（手动输入模式，无 CRM 商机 VO）")
    void check_whenChanceFieldsAreNull_shouldAllow() {
        TenderSubjectConsistencyPolicy.Result result = TenderSubjectConsistencyPolicy.check(
                "山东海化集团", null, null);

        assertThat(result.allowed()).isTrue();
    }

    @Test
    @DisplayName("商机 groupName 与 tenderSubject 都为空字符串 → 放行（手动输入模式）")
    void check_whenChanceFieldsAreEmpty_shouldAllow() {
        TenderSubjectConsistencyPolicy.Result result = TenderSubjectConsistencyPolicy.check(
                "山东海化集团", "", "");

        assertThat(result.allowed()).isTrue();
    }

    @Test
    @DisplayName("商机只有 groupName 有值、tenderSubject 为 null → 仍按 groupName 校验")
    void check_whenOnlyGroupNamePresent_shouldCheckAgainstGroupName() {
        // groupName 匹配 → 放行
        TenderSubjectConsistencyPolicy.Result matchResult = TenderSubjectConsistencyPolicy.check(
                "山东海化集团", "山东海化集团", null);
        assertThat(matchResult.allowed()).isTrue();

        // groupName 不匹配、tenderSubject 为 null → 拒绝
        TenderSubjectConsistencyPolicy.Result rejectResult = TenderSubjectConsistencyPolicy.check(
                "中石化", "山东海化集团", null);
        assertThat(rejectResult.allowed()).isFalse();
        assertThat(rejectResult.errorMessage()).isEqualTo("招标主体不一致，请到 CRM 中修改");
    }
}
