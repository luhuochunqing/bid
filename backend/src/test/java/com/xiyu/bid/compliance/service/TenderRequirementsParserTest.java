// Input: LLM 响应文本（含 markdown 代码围栏、前后解释文字、纯 JSON、非法 JSON）
// Output: 验证 TenderRequirementsParser 容错解析（剥离围栏、提取首个配平 JSON、缺失键兜底、非法 JSON 得 missingSentinel）
// Pos: compliance/service/ - 招标要求 LLM 响应解析单元测试
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.service;

import com.xiyu.bid.compliance.domain.policy.TenderRequirements;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 招标要求 LLM 响应解析器容错测试.
 */
class TenderRequirementsParserTest {

    @Test
    @DisplayName("Markdown 代码围栏包裹的 JSON：正常剥离并解析成功，missing=false")
    void parse_wrappedInMarkdownCodeFences_parsesSuccessfully() {
        String llmResponse = """
                ```json
                {
                  "scoringItems": [
                    { "name": "技术方案", "maxScore": 50.0, "criteria": "方案完整性" }
                  ],
                  "mandatoryQualifications": [
                    "增值电信业务经营许可证",
                    "公安部信息系统安全等级保护备案证明第三级或第三级以上证书"
                  ],
                  "vetoClauses": [
                    { "clause": "未提供投标保证金的无效", "keywords": ["投标保证金"] }
                  ],
                  "compositionRequirements": ["投标函"],
                  "formatRequirements": ["正本一份"]
                }
                ```
                """;

        TenderRequirements req = TenderRequirementsParser.parse(llmResponse);

        assertThat(req.missing()).isFalse();
        assertThat(req.mandatoryQualifications())
                .containsExactly(
                        "增值电信业务经营许可证",
                        "公安部信息系统安全等级保护备案证明第三级或第三级以上证书");
        assertThat(req.scoringItems()).hasSize(1);
        assertThat(req.vetoClauses()).hasSize(1);
    }

    @Test
    @DisplayName("前后带有模型对话解释文字：成功提取首个配平 JSON 对象")
    void parse_withSurroundingExplanatoryText_extractsCorrectly() {
        String llmResponse = """
                好的，已为您从招标文件相关章节中结构化提取出以下要求：
                {
                  "scoringItems": [],
                  "mandatoryQualifications": [
                    "增值电信业务经营许可证"
                  ],
                  "vetoClauses": [],
                  "compositionRequirements": [],
                  "formatRequirements": []
                }
                请注意核实上述资质的有效期与经营范围。
                """;

        TenderRequirements req = TenderRequirementsParser.parse(llmResponse);

        assertThat(req.missing()).isFalse();
        assertThat(req.mandatoryQualifications()).containsExactly("增值电信业务经营许可证");
    }

    @Test
    @DisplayName("包含 depositRequirement 节点：成功解析免收与需缴纳结构")
    void parse_withDepositRequirement_parsesCorrectly() {
        String llmResponse = """
                {
                  "scoringItems": [],
                  "mandatoryQualifications": [],
                  "vetoClauses": [],
                  "compositionRequirements": [],
                  "formatRequirements": [],
                  "depositRequirement": {
                    "required": false,
                    "amount": null,
                    "forms": null,
                    "description": "招标文件前附表第3.4条明确免收投标保证金"
                  }
                }
                """;

        TenderRequirements req = TenderRequirementsParser.parse(llmResponse);

        assertThat(req.missing()).isFalse();
        assertThat(req.depositRequirement()).isNotNull();
        assertThat(req.depositRequirement().required()).isFalse();
        assertThat(req.depositRequirement().description()).contains("明确免收投标保证金");
    }

    @Test
    @DisplayName("包含 bidLetterRequirement 节点：成功解析投标函格式、称谓与有效期")
    void parse_withBidLetterRequirement_parsesCorrectly() {
        String llmResponse = """
                {
                  "scoringItems": [],
                  "mandatoryQualifications": [],
                  "vetoClauses": [],
                  "compositionRequirements": [],
                  "formatRequirements": [],
                  "bidLetterRequirement": {
                    "letterType": "报价函",
                    "recipientName": "上海西域机电系统有限公司",
                    "validityDays": 90,
                    "description": "格式应符合第三章投标文件格式要求之报价函，致上海西域机电系统有限公司，有效期90天"
                  }
                }
                """;

        TenderRequirements req = TenderRequirementsParser.parse(llmResponse);

        assertThat(req.missing()).isFalse();
        assertThat(req.bidLetterRequirement()).isNotNull();
        assertThat(req.bidLetterRequirement().letterType()).isEqualTo("报价函");
        assertThat(req.bidLetterRequirement().recipientName()).isEqualTo("上海西域机电系统有限公司");
        assertThat(req.bidLetterRequirement().validityDays()).isEqualTo(90);
        assertThat(req.bidLetterRequirement().description()).contains("有效期90天");
    }

    @Test
    @DisplayName("包含 authorizationRequirement 节点：成功解析授权委托书要求")
    void parse_withAuthorizationRequirement_parsesCorrectly() {
        String llmResponse = """
                {
                  "scoringItems": [],
                  "mandatoryQualifications": [],
                  "vetoClauses": [],
                  "compositionRequirements": [],
                  "formatRequirements": [],
                  "authorizationRequirement": {
                    "required": true,
                    "allowLegalRepresentativeOnly": true,
                    "templateTitle": "法定代表人身份证明及授权委托书",
                    "description": "法定代表人亲自参加免授权委托书"
                  }
                }
                """;

        TenderRequirements req = TenderRequirementsParser.parse(llmResponse);

        assertThat(req.missing()).isFalse();
        assertThat(req.authorizationRequirement()).isNotNull();
        assertThat(req.authorizationRequirement().required()).isTrue();
        assertThat(req.authorizationRequirement().allowLegalRepresentativeOnly()).isTrue();
        assertThat(req.authorizationRequirement().templateTitle()).isEqualTo("法定代表人身份证明及授权委托书");
        assertThat(req.authorizationRequirement().description()).isEqualTo("法定代表人亲自参加免授权委托书");
    }

    @Test
    @DisplayName("包含 businessLicenseRequirement 节点：成功解析营业执照资质要求")
    void parse_withBusinessLicenseRequirement_parsesCorrectly() {
        String llmResponse = """
                {
                  "scoringItems": [],
                  "mandatoryQualifications": [],
                  "vetoClauses": [],
                  "compositionRequirements": [],
                  "formatRequirements": [],
                  "businessLicenseRequirement": {
                    "required": true,
                    "allowCertificates": ["营业执照", "事业单位法人证书"],
                    "requireOriginalOrCopy": "复印件",
                    "description": "投标人须提供合法有效的营业执照副本复印件（加盖公章），事业单位可提供事业单位法人证书"
                  }
                }
                """;

        TenderRequirements req = TenderRequirementsParser.parse(llmResponse);

        assertThat(req.missing()).isFalse();
        assertThat(req.businessLicenseRequirement()).isNotNull();
        assertThat(req.businessLicenseRequirement().required()).isTrue();
        assertThat(req.businessLicenseRequirement().allowCertificates()).contains("营业执照", "事业单位法人证书");
        assertThat(req.businessLicenseRequirement().requireOriginalOrCopy()).contains("复印件");
        assertThat(req.businessLicenseRequirement().description()).contains("营业执照副本复印件");
    }

    @Test
    @DisplayName("非法 JSON 或纯文本：安全返回 missingSentinel，不抛出异常")
    void parse_invalidJson_returnsMissingSentinel() {
        assertThat(TenderRequirementsParser.parse("这是一段纯文本，没有任何 JSON 结构").missing()).isTrue();
        assertThat(TenderRequirementsParser.parse("{ \"scoringItems\": [ 未闭合").missing()).isTrue();
        assertThat(TenderRequirementsParser.parse(null).missing()).isTrue();
        assertThat(TenderRequirementsParser.parse("   ").missing()).isTrue();
    }
}
