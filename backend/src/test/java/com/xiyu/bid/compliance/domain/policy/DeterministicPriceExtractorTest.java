package com.xiyu.bid.compliance.domain.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicPriceExtractorTest {

    @Test
    @DisplayName("标准阿拉伯数字限价：提取最高投标限价并换算为标准 BigDecimal")
    void extract_standardNumericMaxPrice() {
        String tenderText = """
                第二章 投标须知
                2.1 项目预算与最高限价
                本项目最高投标限价为人民币 3,500,000.00 元整。投标人的投标报价不得超过最高投标限价，否则作废标处理。
                """;

        PriceRequirement req = DeterministicPriceExtractor.extract(tenderText);

        assertThat(req).isNotNull();
        assertThat(req.required()).isTrue();
        assertThat(req.maxPrice()).isEqualByComparingTo(new BigDecimal("3500000.00"));
        assertThat(req.maxPriceRawText()).contains("3,500,000.00");
    }

    @Test
    @DisplayName("万元单位限价：提取并正确乘算为元")
    void extract_tenThousandUnitMaxPrice() {
        String tenderText = """
                三、投标报价要求
                1. 本项目招标控制价：520.5 万元。
                2. 投标报价超过招标控制价的，其投标文件将被拒绝。
                """;

        PriceRequirement req = DeterministicPriceExtractor.extract(tenderText);

        assertThat(req).isNotNull();
        assertThat(req.required()).isTrue();
        assertThat(req.maxPrice()).isEqualByComparingTo(new BigDecimal("5205000"));
        assertThat(req.maxPriceRawText()).contains("520.5 万元");
    }

    @Test
    @DisplayName("中文大写限价：利用 ChineseAmountConverter 自动折算为数字")
    void extract_chineseAmountMaxPrice() {
        String tenderText = """
                第五条 采购预算及最高限价
                本包采购预算及最高限额为人民币：壹仟贰佰万元整。
                """;

        PriceRequirement req = DeterministicPriceExtractor.extract(tenderText);

        assertThat(req).isNotNull();
        assertThat(req.required()).isTrue();
        assertThat(req.maxPrice()).isEqualByComparingTo(new BigDecimal("12000000"));
        assertThat(req.maxPriceRawText()).contains("壹仟贰佰万元整");
    }

    @Test
    @DisplayName("报价方式识别：识别费率报价与总价承包")
    void extract_pricingMethod() {
        String tenderText = """
                本项目采用下浮率报价方式（费率报价），基准费率为 100%，投标人填报下浮率。
                最高限价为 800000 元。
                """;

        PriceRequirement req = DeterministicPriceExtractor.extract(tenderText);

        assertThat(req).isNotNull();
        assertThat(req.pricingMethod()).contains("费率");
        assertThat(req.maxPrice()).isEqualByComparingTo(new BigDecimal("800000"));
    }

    @Test
    @DisplayName("未明确规定最高限价：安全返回默认标准要求")
    void extract_noMaxPrice_returnsStandard() {
        String tenderText = """
                第二章 投标人须知
                请投标人根据市场行情自主填报投标总报价。
                """;

        PriceRequirement req = DeterministicPriceExtractor.extract(tenderText);

        assertThat(req).isNotNull();
        assertThat(req.required()).isTrue();
        assertThat(req.maxPrice()).isNull();
    }

    @Test
    @DisplayName("空输入或空白字符：安全返回 null")
    void extract_emptyOrBlank_returnsNull() {
        assertThat(DeterministicPriceExtractor.extract(null)).isNull();
        assertThat(DeterministicPriceExtractor.extract("   ")).isNull();
    }
}
