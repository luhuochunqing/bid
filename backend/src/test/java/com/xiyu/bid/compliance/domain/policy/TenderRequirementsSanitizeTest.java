// Input: 带引导标点或重复的资质条目
// Output: TenderRequirements 构造时清洗并去重
// Pos: compliance/domain/policy/ - 资质清洗回归（PR !2561 瘦身误删 cleanItem/去重）
package com.xiyu.bid.compliance.domain.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TenderRequirementsSanitizeTest {

    @Test
    @DisplayName("构造时剥离资质条目前导标点，并按原文去重")
    void sanitizeQualifications_stripsLeadingPunctuationAndDeduplicates() {
        TenderRequirements req = new TenderRequirements(
                List.of(),
                List.of("；ISO9001质量管理体系认证", "ISO9001质量管理体系认证", "ISO9001质量管理体系认证"),
                List.of(), List.of(), List.of(), false);

        assertThat(req.mandatoryQualifications()).containsExactly("ISO9001质量管理体系认证");
    }
}
