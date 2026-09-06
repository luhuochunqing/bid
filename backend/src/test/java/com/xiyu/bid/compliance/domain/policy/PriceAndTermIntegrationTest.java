package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PriceAndTermIntegrationTest {

    @Test
    @DisplayName("端到端集成：招标文件提取报价限价与工期质保，标书完全一致直通 PASS")
    void evaluateAll_priceAndTermFullyConsistent_bothPass() {
        String tenderText = """
                招标公告与投标人须知
                最高投标限价：人民币 200 万元整
                工期要求：不超过 180 日历天
                质保期要求：不少于 3 年
                """;

        String bidPage1 = """
                投标函
                致：采购人
                投标总价（大写）：人民币壹佰捌拾万元整
                投标总价（小写）：¥1,800,000.00 元
                工期承诺：150日历天
                质保期承诺：3年
                """;

        String bidPage2 = """
                开标一览表
                投标总报价：1,800,000.00 元
                工期：150日历天
                """;

        String bidPage3 = """
                分项报价明细表
                | 序号 | 项目名称 | 数量 | 单价 | 合价 |
                | 1 | 系统开发 | 1 | 1000000 | 1000000 |
                | 2 | 部署实施 | 1 | 800000 | 800000 |
                合计：1800000.00
                """;

        DocumentContent tenderDoc = DocumentContent.fromText("tender.txt", "thash", tenderText);
        DocumentContent bidDoc = new DocumentContent("bid.pdf", "bhash", "", List.of(
                new DocumentContent.PageContent(1, bidPage1, List.of("投标函")),
                new DocumentContent.PageContent(2, bidPage2, List.of("开标一览表")),
                new DocumentContent.PageContent(3, bidPage3, List.of("分项报价明细表"))
        ));

        TenderRequirements fused = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(), tenderDoc);
        assertThat(fused.priceRequirement()).isNotNull();
        assertThat(fused.businessTermsRequirement()).isNotNull();
        assertThat(fused.businessTermsRequirement().maxScheduleDays()).isEqualTo(180);
        assertThat(fused.businessTermsRequirement().minWarrantyMonths()).isEqualTo(36);

        List<QualityCheckItemResult> results = BidDocumentQualityEngine.evaluateAll(
                bidDoc, tenderDoc, List.of(), fused, Set.of(), null, (pct, msg) -> {}
        );

        Map<String, QualityCheckItemResult> map = results.stream()
                .collect(Collectors.toMap(QualityCheckItemResult::itemCode, r -> r));

        QualityCheckItemResult priceRes = map.get(BidQualityCheckItem.PRICE_CONSISTENCY.getCode());
        assertThat(priceRes).isNotNull();
        assertThat(priceRes.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(priceRes.conclusion()).contains("报价大写与小写金额完全一致");
        assertThat(priceRes.conclusion()).contains("分项报价表 2 项算术核验完全正确");

        QualityCheckItemResult scheduleRes = map.get(BidQualityCheckItem.SCHEDULE_RESPONSE.getCode());
        assertThat(scheduleRes).isNotNull();
        assertThat(scheduleRes.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(scheduleRes.conclusion()).contains("满足招标文件工期上限: 180日历天要求");
    }

    @Test
    @DisplayName("端到端集成：标书分项报价算术错误且工期超期，双项硬拦截 FAIL")
    void evaluateAll_priceArithmeticErrorAndScheduleExceeded_bothFail() {
        String tenderText = """
                最高投标限价：500 万元
                工期要求：120日历天
                """;

        String bidPage1 = """
                投标函
                投标总价（大写）：人民币叁佰万元整
                投标总价（小写）：¥3,000,000.00 元
                工期承诺：160日历天
                """;

        String bidPage2 = """
                分项报价明细表
                | 序号 | 项目名称 | 数量 | 单价 | 合价 |
                | 1 | 核心设备 | 2 | 1000000 | 1500000 |
                合计：3000000.00
                """;

        DocumentContent tenderDoc = DocumentContent.fromText("tender.txt", "thash", tenderText);
        DocumentContent bidDoc = new DocumentContent("bid.pdf", "bhash", "", List.of(
                new DocumentContent.PageContent(1, bidPage1, List.of("投标函")),
                new DocumentContent.PageContent(2, bidPage2, List.of("分项报价明细表"))
        ));

        TenderRequirements fused = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(), tenderDoc);

        List<QualityCheckItemResult> results = BidDocumentQualityEngine.evaluateAll(
                bidDoc, tenderDoc, List.of(), fused, Set.of(), null, (pct, msg) -> {}
        );

        Map<String, QualityCheckItemResult> map = results.stream()
                .collect(Collectors.toMap(QualityCheckItemResult::itemCode, r -> r));

        QualityCheckItemResult priceRes = map.get(BidQualityCheckItem.PRICE_CONSISTENCY.getCode());
        assertThat(priceRes.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(priceRes.conclusion()).contains("算术计算错误");

        QualityCheckItemResult scheduleRes = map.get(BidQualityCheckItem.SCHEDULE_RESPONSE.getCode());
        assertThat(scheduleRes.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(scheduleRes.conclusion()).contains("超过招标文件最高允许工期（120日历天）");
    }
}
