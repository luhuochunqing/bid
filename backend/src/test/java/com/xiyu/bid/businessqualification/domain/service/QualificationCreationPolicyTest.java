package com.xiyu.bid.businessqualification.domain.service;

import com.xiyu.bid.businessqualification.domain.model.BusinessQualification;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationCategory;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationSubject;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationSubjectType;
import com.xiyu.bid.businessqualification.domain.valueobject.ValidityPeriod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QualificationCreationPolicy 纯核心测试。
 * CO-525: agencyContact 从"代理联系方式"改为"代理机构联系人"，仅保留必填校验，不再限制手机/固话/邮箱格式。
 */
class QualificationCreationPolicyTest {

    private final QualificationCreationPolicy policy = new QualificationCreationPolicy();

    private BusinessQualification sampleWithAgencyContact(String agencyContact) {
        return BusinessQualification.create(
                null,
                "ISO 9001",
                "AAA",
                QualificationSubject.of(QualificationSubjectType.COMPANY, "西域"),
                QualificationCategory.LICENSE,
                "CERT-001",
                "认证机构",
                "代理机构",
                agencyContact,
                "范围",
                null,
                null,
                new ValidityPeriod(LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1)),
                null,
                null,
                null,
                null
        );
    }

    @Test
    @DisplayName("CO-525 代理机构联系人为纯文本时校验通过")
    void validateForCreate_AgencyContactIsPlainText_ShouldPass() {
        QualificationValidationResult result = policy.validateForCreate(sampleWithAgencyContact("张三"));
        assertThat(result.valid()).isTrue();
    }

    @Test
    @DisplayName("CO-525 代理机构联系人仍为必填")
    void validateForCreate_AgencyContactBlank_ShouldFail() {
        QualificationValidationResult result = policy.validateForCreate(sampleWithAgencyContact("  "));
        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("代理机构联系人").contains("不能为空");
    }

    @Test
    @DisplayName("CO-525 代理机构联系人为手机号仍可通过")
    void validateForCreate_AgencyContactIsPhone_ShouldPass() {
        QualificationValidationResult result = policy.validateForCreate(sampleWithAgencyContact("13800138000"));
        assertThat(result.valid()).isTrue();
    }

    @Test
    @DisplayName("CO-525 代理机构联系人包含中文姓名+号码可通过")
    void validateForCreate_AgencyContactNameWithPhone_ShouldPass() {
        QualificationValidationResult result = policy.validateForCreate(sampleWithAgencyContact("张三 13800138000"));
        assertThat(result.valid()).isTrue();
    }
}
