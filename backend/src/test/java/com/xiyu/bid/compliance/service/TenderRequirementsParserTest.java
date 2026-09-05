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
    @DisplayName("非法 JSON 或纯文本：安全返回 missingSentinel，不抛出异常")
    void parse_invalidJson_returnsMissingSentinel() {
        assertThat(TenderRequirementsParser.parse("这是一段纯文本，没有任何 JSON 结构").missing()).isTrue();
        assertThat(TenderRequirementsParser.parse("{ \"scoringItems\": [ 未闭合").missing()).isTrue();
        assertThat(TenderRequirementsParser.parse(null).missing()).isTrue();
        assertThat(TenderRequirementsParser.parse("   ").missing()).isTrue();
    }
}
