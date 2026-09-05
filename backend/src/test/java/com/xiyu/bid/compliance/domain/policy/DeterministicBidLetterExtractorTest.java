package com.xiyu.bid.compliance.domain.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicBidLetterExtractorTest {

    @Test
    @DisplayName("提取标准投标函要求：包含招标人全称、函件类型与投标有效期天数")
    void testExtract_StandardBidLetterRequirements() {
        String tenderText = """
                第一章 招标公告
                项目名称：某某数字化采购平台项目
                招标人：张家口银行股份有限公司
                二、投标人须知前附表
                | 3.3.1 | 投标函格式 | 投标人必须按照第六章投标文件格式《投标函》格式填写 |
                | 3.3.2 | 投标有效期 | 90日历天，自投标截止之日起算 |
                """;

        TenderRequirements.BidLetterRequirement req = DeterministicBidLetterExtractor.extract(tenderText);

        assertThat(req).isNotNull();
        assertThat(req.letterType()).isEqualTo("投标函");
        assertThat(req.recipientName()).contains("张家口银行股份有限公司");
        assertThat(req.validityDays()).isEqualTo(90);
    }

    @Test
    @DisplayName("提取谈判采购报价函要求：识别报价函类型与有效期")
    void testExtract_QuotationLetterRequirements() {
        String tenderText = """
                竞争性谈判采购文件
                采购人：中国雅江集团物资设备有限公司
                前附表：
                3.1 响应函件：投标人须提交《报价函》及分项报价清单。
                3.2 响应文件有效期为 60 天。
                """;

        TenderRequirements.BidLetterRequirement req = DeterministicBidLetterExtractor.extract(tenderText);

        assertThat(req).isNotNull();
        assertThat(req.letterType()).isEqualTo("报价函");
        assertThat(req.recipientName()).contains("中国雅江集团物资设备有限公司");
        assertThat(req.validityDays()).isEqualTo(60);
    }

    @Test
    @DisplayName("招标文件无投标函格式特殊要求时，返回 null（交由 LLM 或标书默认公文格式校验）")
    void testExtract_WhenNoRequirements_ReturnsNull() {
        String tenderText = "项目技术规格书：关于服务器与网络设备技术要求。";

        TenderRequirements.BidLetterRequirement req = DeterministicBidLetterExtractor.extract(tenderText);

        assertThat(req).isNull();
    }

    @Test
    @DisplayName("双轨融合 fuseWithDeterministic：天数与称谓双有数时，以确定性正则提取的原文为准，保留 LLM 丰富描述")
    void testFuseWithDeterministic_DeterministicWinsOnHardFacts() {
        TenderRequirements.BidLetterRequirement llmReq = new TenderRequirements.BidLetterRequirement(
                "投标函", "张家口银行", 90, "投标人应按照招标文件格式编制投标函并盖公章");
        TenderRequirements.BidLetterRequirement detReq = new TenderRequirements.BidLetterRequirement(
                "投标函", "张家口银行股份有限公司", 90, "招标文件条款提取：张家口银行股份有限公司");

        TenderRequirements.BidLetterRequirement fused = TenderRequirements.BidLetterRequirement.fuseWithDeterministic(
                llmReq, detReq);

        assertThat(fused).isNotNull();
        assertThat(fused.recipientName())
                .as("称谓应以确定性提取的完整公司全称为准")
                .isEqualTo("张家口银行股份有限公司");
        assertThat(fused.validityDays()).isEqualTo(90);
        assertThat(fused.description()).contains("按照招标文件格式编制");
    }
}
