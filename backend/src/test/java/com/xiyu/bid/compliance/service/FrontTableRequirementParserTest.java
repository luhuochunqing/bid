package com.xiyu.bid.compliance.service;

import com.xiyu.bid.compliance.domain.policy.FrontTableRequirement;
import com.xiyu.bid.compliance.domain.policy.TenderRequirements;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FrontTableRequirementParserTest {

    @Test
    @DisplayName("前附表要求解析：从完整 JSON 响应中反序列化前附表契约")
    void testParse_FrontTableRequirement() {
        String llmResponse = """
                {
                  "scoringItems": [],
                  "mandatoryQualifications": [],
                  "vetoClauses": [],
                  "compositionRequirements": [],
                  "formatRequirements": [],
                  "frontTableRequirement": {
                    "required": true,
                    "projectName": "西域数智化平台",
                    "tenderProjectNo": "XY-2026-001",
                    "deliveryPeriod": "60日历天",
                    "warrantyPeriod": "3年",
                    "validityPeriod": "90日历天",
                    "deliveryLocation": "上海市",
                    "paymentTerms": "分期付款",
                    "qualityStandard": "国家合格标准",
                    "keyClauses": {
                      "履约保证金": "10%"
                    },
                    "description": "前附表关键条款要求"
                  }
                }
                """;

        TenderRequirements req = TenderRequirementsParser.parse(llmResponse);

        assertThat(req.missing()).isFalse();
        FrontTableRequirement ft = req.frontTableRequirement();
        assertThat(ft).isNotNull();
        assertThat(ft.required()).isTrue();
        assertThat(ft.projectName()).isEqualTo("西域数智化平台");
        assertThat(ft.tenderProjectNo()).isEqualTo("XY-2026-001");
        assertThat(ft.deliveryPeriod()).isEqualTo("60日历天");
        assertThat(ft.warrantyPeriod()).isEqualTo("3年");
        assertThat(ft.validityPeriod()).isEqualTo("90日历天");
        assertThat(ft.deliveryLocation()).isEqualTo("上海市");
        assertThat(ft.paymentTerms()).isEqualTo("分期付款");
        assertThat(ft.qualityStandard()).isEqualTo("国家合格标准");
        assertThat(ft.keyClauses()).containsEntry("履约保证金", "10%");
    }
}
