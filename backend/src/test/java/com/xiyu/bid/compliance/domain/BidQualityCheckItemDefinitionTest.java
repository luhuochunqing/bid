// Input: BidQualityCheckItem 枚举 25 项定义
// Output: 断言名称/检查内容与 PRD 2.3 检查项定义表逐一对齐（防漂移）
// Pos: compliance/domain/ - 25 项检查项定义契约测试（PRD 2.3）
package com.xiyu.bid.compliance.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class BidQualityCheckItemDefinitionTest {

    /** PRD 2.3 检查项定义表：code → 名称. */
    private static final Map<String, String> PRD_NAMES = Map.ofEntries(
            Map.entry("COVER_INFO", "封面信息完整性"),
            Map.entry("BID_LETTER_FORMAT", "投标函格式规范"),
            Map.entry("AUTHORIZATION_LETTER", "授权委托书"),
            Map.entry("BUSINESS_LICENSE", "营业执照"),
            Map.entry("QUALIFICATION_MATCH", "资质匹配"),
            Map.entry("PRICE_CONSISTENCY", "报价一致性"),
            Map.entry("CURRENCY_UNIT", "货币单位"),
            Map.entry("SCHEDULE_RESPONSE", "工期响应"),
            Map.entry("SIGNATURE_SEAL", "签章完整性"),
            Map.entry("CONTACT_INFO", "联系信息"),
            Map.entry("QUALIFICATION_VETO", "资格条件一票否决"),
            Map.entry("FRONT_TABLE_RESPONSE", "前附表关键条款填写完整性"),
            Map.entry("RESPONSIVENESS_REVIEW", "响应性评审条款"),
            Map.entry("QUALIFICATION_REVIEW", "资格性评审条款响应"),
            Map.entry("SCORING_RESPONSE", "评分项响应（按满分标准）"),
            Map.entry("DOCUMENT_COMPLETENESS", "投标文件组成完整性"),
            Map.entry("TEMPLATE_FORMAT", "格式模板符合性"),
            Map.entry("VETO_KEYWORD", "投标否决项关键字扫描"),
            Map.entry("CLARIFICATION_RESPONSE", "澄清文件响应"),
            Map.entry("COMMITMENT_SEAL", "承诺类文件盖章检查"),
            Map.entry("ID_AUTH_LETTER", "委托人身份证原件 + 授权委托书"),
            Map.entry("BID_OPENING_TABLE", "开标一览表/报价表"),
            Map.entry("DEPOSIT_RECEIPT", "投标保证金回单"),
            Map.entry("CA_CERTIFICATE", "电子标 CA 证书"),
            Map.entry("DEPOSIT_REFUND", "保证金退款信息表 / 收据"));

    @Test
    @DisplayName("契约：25 项名称与 PRD 2.3 检查项定义表一致（防漂移）")
    void testTitles_AlignWithPrdNames() {
        Map<String, String> actual = Arrays.stream(BidQualityCheckItem.values())
                .collect(Collectors.toMap(BidQualityCheckItem::getCode, BidQualityCheckItem::getTitle));
        assertThat(actual).isEqualTo(PRD_NAMES);
    }

    @Test
    @DisplayName("契约：25 项检查项说明（检查内容）均非空")
    void testDescriptions_AllNonBlank() {
        assertThat(Arrays.stream(BidQualityCheckItem.values()).toList())
                .allSatisfy(item -> assertThat(item.getDescription()).isNotBlank());
    }

    @Test
    @DisplayName("契约：签章完整性说明如实标注非分页文档（.docx）能力边界")
    void testSignatureSealDescription_StatesNonPaginatedBoundary() {
        String description = BidQualityCheckItem.SIGNATURE_SEAL.getDescription();

        assertThat(description).contains("非分页文档");
        assertThat(description).contains("人工复核");
        assertThat(description).doesNotContain("通过 AI 图像识别检测投标文件各页签章");
    }

    @Test
    @DisplayName("契约：抽样检查项说明与 PRD 2.3 检查内容一致")
    void testDescriptions_SamplesAlignWithPrd() {
        assertThat(BidQualityCheckItem.AUTHORIZATION_LETTER.getDescription()).isEqualTo(
                "检查法人授权委托书是否存在、是否加盖公章、授权范围和期限是否明确，是否符合招标文件模板要求");
        assertThat(BidQualityCheckItem.VETO_KEYWORD.getDescription()).isEqualTo(
                "从招标文件动态提取否决项关键词，对投标文件全文进行扫描。检测到关键词为不通过，记录页码和原文上下文");
        assertThat(BidQualityCheckItem.DEPOSIT_REFUND.getDescription()).isEqualTo(
                "检查退款账户信息表和收据存根是否齐全、信息是否完整");
    }
}
