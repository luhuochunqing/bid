package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.DocumentContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicRequirementsFuserTest {

    @Test
    @DisplayName("确定性融合器：全量融合资质、前附表、CA等各分项硬事实")
    void testFuseAll_Success() {
        String tenderText = """
                第二章 投标人须知前附表
                工期要求：合同签订后90日历天
                质保期：验收合格后24个月
                投标有效期：90日历天
                交货地点：北京市海淀区
                付款方式：验收合格支付100%
                质量标准：合格
                本项目免收投标保证金。

                三、投标人资格要求
                1. 供应商须具备以下资质证书：
                （1）ISO9001 质量管理体系认证证书
                """;
        DocumentContent tenderDoc = DocumentContent.fromText("tender.pdf", "hash_fuse", tenderText);
        TenderRequirements base = new TenderRequirements(
                List.of(), List.of(), List.of(), List.of(), List.of(), false);

        TenderRequirements fused = DeterministicRequirementsFuser.fuseAll(base, tenderDoc);

        assertThat(fused).isNotNull();
        assertThat(fused.mandatoryQualifications().stream().anyMatch(q -> q.contains("ISO9001"))).isTrue();
        assertThat(fused.depositRequirement()).isNotNull();
        assertThat(fused.depositRequirement().required()).isFalse();
        assertThat(fused.frontTableRequirement()).isNotNull();
        assertThat(fused.frontTableRequirement().deliveryPeriod()).contains("90日历天");
        assertThat(fused.frontTableRequirement().warrantyPeriod()).contains("24个月");
        assertThat(fused.frontTableRequirement().validityPeriod()).contains("90日历天");
    }
}
