// Input: 明确业务契约或已标记的历史启发式观察输入
// Output: 保留原测试断言；语料不作为默认门禁
// Pos: compliance/domain/policy - semantic boundary 技术债分层测试
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.service.LlmVerdictParser;
import com.xiyu.bid.compliance.service.TenderRequirementsParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QualityVerdictParsingContractTest {

    @Test
    void longFieldFragmentsPreserveParsingAndRejectMalformedValues() {
        String longValue = "核验内容".repeat(4000);
        for (String field : List.of("conclusion", "suggestion")) {
            String prefix = "判定：\"status\":\"PASS\","
                    + (field.equals("suggestion") ? "\"conclusion\":\"完整结论\"," : "")
                    + "\"" + field + "\":\"";
            assertThat(LlmVerdictParser.parse(prefix + longValue + "\""))
                    .isPresent();
            assertThat(LlmVerdictParser.parse(prefix + longValue))
                    .isEmpty();
            assertThat(LlmVerdictParser.parse(prefix + longValue + "\", \"status\":null"))
                    .isEmpty();
        }
    }

    @Test
    void invalidTopLevelVerdictCannotBorrowNestedStatus() {
        for (String response : List.of(
                "{\"status\":null,\"conclusion\":\"无法判定\",\"example\":{\"status\":\"PASS\"}}",
                "{\"status\":\"UNKNOWN\",\"example\":{\"status\":\"PASS\"}}",
                "{\"conclusion\":\"无法判定\",\"example\":{\"status\":\"PASS\"}",
                "{\"status\":null,\"conclusion\":\"无法判定\"} {\"status\":\"PASS\"}")) {
            assertThat(LlmVerdictParser.parse(response))
                    .as("无效顶层裁决不得借用示例或后续对象：%s", response).isEmpty();
        }
    }

    @Test
    void malformedVerdictCannotCombineFieldsAcrossObjects() {
        for (String response : List.of(
                "{\"conclusion\":\"无法判定\",} {\"status\":\"PASS\"}",
                "\"conclusion\":\"无法判定\", 另一对象：\"status\":\"PASS\"",
                "\"status\":null, \"example\":{\"status\":\"PASS\"}")) {
            assertThat(LlmVerdictParser.parse(response))
                    .as("字段必须来自同一个裁决对象：%s", response).isEmpty();
        }
    }

    @Test
    void fieldFragmentRequiresCompleteNonBlankConclusion() {
        for (String status : List.of("PASS", "FAIL", "MANUAL")) {
            String prefix = "\"status\":\"" + status + "\"";
            for (String suffix : List.of("", ",", ",\"conclusion\":\"\"",
                    ",\"conclusion\":\"   \"", ",\"conclusion\":\"未完成",
                    ",\"suggestion\":\"请核查材料\"", ",\"conclusion\":null",
                    ",\"conclusion\":123")) {
                assertThat(LlmVerdictParser.parse(prefix + suffix))
                        .as("字段恢复不得补造结论：%s", prefix + suffix).isEmpty();
            }
            for (String suffix : List.of("", ",")) {
                assertThat(LlmVerdictParser.parse(
                        prefix + ",\"conclusion\":\"完整结论\"" + suffix))
                        .hasValueSatisfying(verdict -> {
                            assertThat(verdict.status()).isEqualTo(status);
                            assertThat(verdict.conclusion()).isEqualTo("完整结论");
                        });
            }
        }
    }

    @Test
    void truncatedArrayObjectsCannotReplaceCompletedVerdictFields() {
        String prefix = "{\"status\":\"FAIL\",\"conclusion\":\"缺少原始凭证\","
                + "\"suggestion\":\"请补充凭证\",\"reasonCode\":\"EVIDENCE_INSUFFICIENT\",\"details\":[";
        for (String tail : List.of(
                "{\"status\":\"PASS\",\"conclusion\":\"未完成",
                "{\"page\":1}, {\"status\":\"PASS\",\"conclusion\":\"不得借用\"",
                "{\"status\":\"PASS\",\"conclusion\":\"不得借用\", ")) {
            assertThat(LlmVerdictParser.parse(prefix + tail)).as(tail)
                    .contains(new LlmVerdict("FAIL", "EVIDENCE_INSUFFICIENT", "缺少原始凭证", "请补充凭证"));
        }
    }

    @Test
    void truncatedArrayStringsAndNestedContainersPreserveOnlyCompletedVerdict() {
        String prefix = "{\"status\":\"MANUAL\",\"conclusion\":\"需核对原图\","
                + "\"reasonCode\":\"LOW_CONFIDENCE\",";
        for (String tail : List.of(
                "\"details\":[\"未完成",
                "\"details\":[\"完整线索\", \"未完成",
                "\"details\":[\"包含转义\\\"引号及括号]}但未完成",
                "\"details\":{\"pages\":[[1,2",
                "\"details\":{\"pages\":[{\"page\":1}, ")) {
            assertThat(LlmVerdictParser.parse(prefix + tail)).as(tail)
                    .contains(new LlmVerdict("MANUAL", "LOW_CONFIDENCE", "需核对原图", ""));
        }
    }

    @Test
    void truncatedArraysCannotSupplyMissingTopLevelConclusionOrStatus() {
        for (String response : List.of(
                "{\"status\":\"PASS\",\"details\":[{\"conclusion\":\"不得借用\"",
                "{\"conclusion\":\"顶层结论\",\"details\":[{\"status\":\"PASS\"",
                "{\"status\":\"PASS\",\"conclusion\":\"\",\"details\":[\"未完成",
                "{\"status\":\"PASS\",\"conclusion\":null,\"details\":{\"pages\":[1")) {
            assertThat(LlmVerdictParser.parse(response)).as(response).isEmpty();
        }
    }

    @Test
    void truncatedVerdictRequiresCompleteNonBlankConclusion() {
        for (String status : List.of("PASS", "FAIL", "MANUAL")) {
            String prefix = "{\"status\":\"" + status + "\"";
            for (String suffix : List.of("", ",\"conclusion\":\"", ",\"conclusion\":\"尚未输出完整结论",
                    ",\"conclusion\":\"结论包含转义引号\\\"但仍未结束", ",\"conclusion\":\"\"",
                    ",\"conclusion\":\"   \"", ",\"conclusion\":null")) {
                String response = prefix + suffix;
                assertThat(LlmVerdictParser.parse(response))
                        .as("截断响应不得补造裁决：%s", response).isEmpty();
            }
            var complete = LlmVerdictParser.parse(
                    prefix + ",\"conclusion\":\"已完整输出的结论\"");
            assertThat(complete).hasValueSatisfying(verdict -> {
                assertThat(verdict.status()).isEqualTo(status);
                assertThat(verdict.conclusion()).isEqualTo("已完整输出的结论");
            });
        }
    }

    @Test
    @DisplayName("大模型响应末尾截断或丢失闭合花括号时，解析器自动修复并成功提取裁决结果")
    void truncatedOrMalformedLlmResponseIsHealedSuccessfully() {
        // 场景 1：结论已完整输出，仅建议截断；丢弃未完成建议，保留完整裁决。
        String truncated = """
                {
                  "status": "PASS",
                  "conclusion": "投标文件对各项评分标准响应完整，无占位符留白，符合满分标准。",
                  "suggestion": "建议在后续执行中保持发货时效
                """;
        var parsed1 = LlmVerdictParser.parse(truncated);
        assertThat(parsed1).isPresent();
        assertThat(parsed1.get().status()).isEqualTo("PASS");
        assertThat(parsed1.get().conclusion()).contains("符合满分标准");
        assertThat(parsed1.get().suggestion()).isEmpty();

        // 场景 2：仅丢失最外层花括号
        String missingBrace = """
                {"status": "MANUAL", "conclusion": "需人工核对系统报价", "reasonCode": "LOW_CONFIDENCE"
                """;
        var parsed2 = LlmVerdictParser.parse(missingBrace);
        assertThat(parsed2).isPresent();
        assertThat(parsed2.get().status()).isEqualTo("MANUAL");
        assertThat(parsed2.get().reasonCode()).isEqualTo("LOW_CONFIDENCE");

        // 场景 3：无外层对象的连续字段补齐后仍经 JSON 结构校验
        String brokenJson = "AI判定结果：\"status\": \"PASS\", \"conclusion\": \"响应充分\", 详情见后文...";
        var parsed3 = LlmVerdictParser.parse(brokenJson);
        assertThat(parsed3).isPresent();
        assertThat(parsed3.get().status()).isEqualTo("PASS");
        assertThat(parsed3.get().conclusion()).isEqualTo("响应充分");
    }

    @Test
    @DisplayName("截断评分要求在解析与合并链路均保持缺失哨兵")
    void tenderRequirementsParserRejectsTruncatedScoringBeforeMerge() {
        String truncatedJson = """
                {
                  "mandatoryQualifications": [ "增值电信业务经营许可证" ],
                  "scoringItems": [
                    {
                      "name": "投标人业绩",
                      "maxScore": 15,
                      "criteria": "按照资格要求审核"
                    },
                    {
                      "name": "投标体系认证",
                      "maxScore": 10,
                      "criteria": "ISO认证
                """;
        var parsed = TenderRequirementsParser.parse(truncatedJson);
        assertThat(parsed).isEqualTo(TenderRequirements.missingSentinel());
        assertThat(TenderRequirements.merge(List.of(parsed)))
                .isEqualTo(TenderRequirements.missingSentinel());
        assertThat(parsed.missing()).isTrue();
    }

}
