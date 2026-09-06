package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.DocumentContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicFrontTableExtractorTest {

    @Test
    @DisplayName("前附表提取：从前附表正文中抽取工期、质保期、有效期、交货地点与付款方式")
    void testExtract_FromFrontTableText() {
        String text = """
                第二章 投标人须知
                第一节 投标人须知前附表
                1.1.2 招标项目名称：西域数字化采购平台二期工程
                1.1.3 招标编号：XY-2026-ZB-0888
                1.3.1 供货周期/工期：合同签订后60日历天完成交付
                1.3.2 质保期：验收合格之日起36个月
                3.3.1 投标有效期：投标截止日起90日历天
                1.3.3 交货地点：上海市嘉定区工业园区
                1.3.4 付款方式：货到初验支付70%，终验合格支付20%，质保金10%
                1.3.5 质量标准：国家合格标准及行业规范
                """;
        DocumentContent tenderDoc = DocumentContent.fromText("tender.pdf", "hash1", text);

        FrontTableRequirement req = DeterministicFrontTableExtractor.extract(tenderDoc);

        assertThat(req).isNotNull();
        assertThat(req.deliveryPeriod()).contains("60日历天");
        assertThat(req.warrantyPeriod()).contains("36个月");
        assertThat(req.validityPeriod()).contains("90日历天");
        assertThat(req.deliveryLocation()).contains("上海市嘉定区");
        assertThat(req.paymentTerms()).contains("货到初验支付70%");
        assertThat(req.qualityStandard()).contains("国家合格标准");
    }

    @Test
    @DisplayName("前附表提取：无前附表或空文档返回空契约")
    void testExtract_WhenEmptyOrMissing() {
        DocumentContent doc = DocumentContent.fromText("tender.pdf", "hash2", "普通技术要求说明");

        FrontTableRequirement req = DeterministicFrontTableExtractor.extract(doc);

        assertThat(req).isNotNull();
        assertThat(req.deliveryPeriod()).isNull();
        assertThat(req.warrantyPeriod()).isNull();
    }

    @Test
    @DisplayName("前附表融合：fuse 算法硬事实优先")
    void testFuse_PrefersNonBlank() {
        FrontTableRequirement r1 = new FrontTableRequirement(true, "项目A", "ZB001", "30天", null, "90天", null, null, null, Map.of(), "描述1");
        FrontTableRequirement r2 = new FrontTableRequirement(null, null, null, "45天", "2年", null, "北京", "分期付", "优良", Map.of("key", "val"), "描述2");

        FrontTableRequirement fused = FrontTableRequirement.fuse(r1, r2);

        assertThat(fused.required()).isTrue();
        assertThat(fused.projectName()).isEqualTo("项目A");
        assertThat(fused.deliveryPeriod()).isEqualTo("45天");
        assertThat(fused.warrantyPeriod()).isEqualTo("2年");
        assertThat(fused.validityPeriod()).isEqualTo("90天");
        assertThat(fused.deliveryLocation()).isEqualTo("北京");
        assertThat(fused.paymentTerms()).isEqualTo("分期付");
        assertThat(fused.qualityStandard()).isEqualTo("优良");
        assertThat(fused.keyClauses()).containsEntry("key", "val");
    }
}
