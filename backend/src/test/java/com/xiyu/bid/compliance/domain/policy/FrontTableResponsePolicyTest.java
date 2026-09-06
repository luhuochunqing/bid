package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FrontTableResponsePolicyTest {

    @Test
    @DisplayName("前附表核查：标书包含完整填报的前附表关键条款响应表时直通 PASS")
    void testCheck_WhenFullyFilled_Passes() {
        String bidText = """
                第三章 商务响应
                第一节 投标人须知前附表关键条款响应表
                项目名称：西域数字化采购平台二期工程
                工期响应：承诺合同签订后60日历天内全部交付完毕，满足招标要求。
                质保期响应：承诺提供自终验合格之日起36个月免费质保服务。
                投标有效期响应：承诺本投标文件有效期为90日历天。
                质量标准响应：承诺达到国家合格标准及行业规范。
                交货地点响应：承诺免费运输并交付至上海市嘉定区指定现场。
                付款方式响应：完全同意并响应招标文件的付款进度安排。
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash1", bidText);
        FrontTableRequirement req = new FrontTableRequirement(true, "西域数字化采购平台二期工程", null, "60日历天", "36个月", "90日历天", "上海市嘉定区", "货到付款", "合格", Map.of(), null);

        QualityCheckItemResult result = FrontTableResponsePolicy.check(doc, req);

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(result.conclusion()).contains("前附表关键条款填写完整");
        assertThat(result.evidences()).isNotEmpty();
    }

    @Test
    @DisplayName("前附表反假：检测到下划线未填留白时判定 FAIL 拦截")
    void testCheck_WhenUnderlineBlanksPresent_Fails() {
        String bidText = """
                第一节 投标人须知前附表响应表
                工期要求：响应招标要求，工期为_______日历天
                质保期要求：质保期为______年
                投标有效期：有效期为______天
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash2", bidText);

        QualityCheckItemResult result = FrontTableResponsePolicy.check(doc, FrontTableRequirement.empty());

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.conclusion()).contains("占位符或下划线留白");
        assertThat(result.suggestion()).contains("补全");
    }

    @Test
    @DisplayName("前附表反假：检测到括号提示占位符未填时判定 FAIL 拦截")
    void testCheck_WhenPromptPlaceholdersPresent_Fails() {
        String bidText = """
                第二章 前附表响应
                交货期：【请在此处填写具体交货期限】
                质保承诺：【填写质保期限及售后承诺】
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash3", bidText);

        QualityCheckItemResult result = FrontTableResponsePolicy.check(doc, FrontTableRequirement.empty());

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.conclusion()).contains("占位符");
    }

    @Test
    @DisplayName("前附表反假：目录点线页不判定为正文响应")
    void testCheck_WhenOnlyInCatalog_Fails() {
        String bidText = "目录\n一、投标人须知前附表关键条款响应表........................12";
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash4", bidText);

        QualityCheckItemResult result = FrontTableResponsePolicy.check(doc, FrontTableRequirement.empty());

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.conclusion()).contains("仅在目录中");
    }

    @Test
    @DisplayName("前附表核查：未检测到前附表章节时返回 null 留交 LLM 裁决")
    void testCheck_WhenNotDetected_ReturnsNull() {
        String bidText = "普通商务通用承诺函";
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash5", bidText);

        QualityCheckItemResult result = FrontTableResponsePolicy.check(doc, FrontTableRequirement.empty());

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("前附表表格完整性：前附表响应表格存在空单元格时判定 FAIL 拦截")
    void testCheck_WhenTableCellEmpty_Fails() {
        String bidText = """
                第一节 投标人须知前附表关键条款响应表
                | 序号 | 关键条款 | 招标要求 | 响应情况 | 偏离说明 |
                |---|---|---|---|---|
                | 1 | 工期 | 60日历天 | 响应60天 | / |
                | 2 | 质保期 | 36个月 |  | 无偏离 |
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash6", bidText);

        QualityCheckItemResult result = FrontTableResponsePolicy.check(doc, FrontTableRequirement.empty());

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.conclusion()).contains("填写不完整");
        assertThat(result.evidences()).isNotEmpty();
        assertThat(result.evidences().getFirst().quote()).contains("第2行").contains("响应情况");
    }

    @Test
    @DisplayName("前附表表格完整性：前附表响应表格在非偏离列存在斜杠留白时判定 FAIL 拦截")
    void testCheck_WhenTableCellSlashBlank_Fails() {
        String bidText = """
                第一节 投标人须知前附表关键条款响应表
                | 序号 | 关键条款 | 招标要求 | 响应情况 |
                |---|---|---|---|
                | 1 | 工期 | 60日历天 | / |
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash7", bidText);

        QualityCheckItemResult result = FrontTableResponsePolicy.check(doc, FrontTableRequirement.empty());

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.conclusion()).contains("填写不完整");
        assertThat(result.evidences().getFirst().quote()).contains("斜杠");
    }

    @Test
    @DisplayName("前附表表格完整性：表格完整填报且偏离列允许斜杠时直通 PASS")
    void testCheck_WhenTableWellFilledWithDeviationExemption_Passes() {
        String bidText = """
                第一节 投标人须知前附表关键条款响应表
                | 序号 | 关键条款 | 招标要求 | 响应情况 | 偏离说明 |
                |---|---|---|---|---|
                | 1 | 工期 | 60日历天 | 响应60日历天完工 | / |
                | 2 | 质保期 | 36个月 | 承诺36个月免费保修 | 无 |
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash8", bidText);

        QualityCheckItemResult result = FrontTableResponsePolicy.check(doc, FrontTableRequirement.empty());

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(result.conclusion()).contains("前附表关键条款填写完整");
    }
}
