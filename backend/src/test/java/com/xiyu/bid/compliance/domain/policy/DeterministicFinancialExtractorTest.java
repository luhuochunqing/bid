// Input: 真实与模拟招标文件财务要求文本（含具体年份、相对年份、免除场景等）
// Output: 验证 DeterministicFinancialExtractor 纯核心规则提取的准确性、年份提取与双轨融合
// Pos: compliance/domain/policy/ - 财务状况提取器契约单元测试
package com.xiyu.bid.compliance.domain.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicFinancialExtractorTest {

    @Test
    @DisplayName("提取标准三年审计报告要求：识别审计报告、具体年份与财务三表")
    void testExtract_StandardThreeYearsAuditReport() {
        String tenderText = """
                第二章 投标人须知
                3.5.1 财务要求：
                投标人须提供2021年度、2022年度、2023年度经会计师事务所或审计机构审计的财务会计报表，
                包括资产负债表、利润表、现金流量表，并加盖会计师事务所印章。
                """;

        FinancialRequirement req = DeterministicFinancialExtractor.extract(tenderText);

        assertThat(req).isNotNull();
        assertThat(req.required()).isTrue();
        assertThat(req.auditReportRequired()).isTrue();
        assertThat(req.requireCpaSeal()).isTrue();
        assertThat(req.requiredYears()).contains("2021", "2022", "2023");
        assertThat(req.requiredStatements()).contains("资产负债表", "利润表", "现金流量表");
        assertThat(req.description()).contains("经会计师事务所审计");
    }

    @Test
    @DisplayName("提取相对年份与报表附注要求：识别近三年及附注要求")
    void testExtract_RelativeYearsAndNotes() {
        String tenderText = """
                前附表：
                财务状况要求：须提供近三年经审计的财务报告及财务报表附注。
                """;

        FinancialRequirement req = DeterministicFinancialExtractor.extract(tenderText);

        assertThat(req).isNotNull();
        assertThat(req.requiredYears()).contains("近三年");
        assertThat(req.auditReportRequired()).isTrue();
        assertThat(req.requiredStatements()).contains("财务报表附注");
    }

    @Test
    @DisplayName("显式免除短语：精准识别为免除/不涉及")
    void testExtract_ExplicitExemption_ReturnsNotRequired() {
        String tenderText = "本项目属于服务类小额采购，无需提供财务状况证明或财务审计报告。";

        FinancialRequirement req = DeterministicFinancialExtractor.extract(tenderText);

        assertThat(req).isNotNull();
        assertThat(req.required()).isFalse();
        assertThat(DeterministicFinancialExtractor.isFinancialNotRequired(tenderText)).isTrue();
    }

    @Test
    @DisplayName("无任何财务关键词时：返回 null，不误判")
    void testExtract_WhenNoFinancialKeywords_ReturnsNull() {
        String tenderText = "技术规格书：关于服务器、交换机与路由器网络架构规划。";

        FinancialRequirement req = DeterministicFinancialExtractor.extract(tenderText);

        assertThat(req).isNull();
        assertThat(DeterministicFinancialExtractor.isFinancialNotRequired(tenderText)).isFalse();
    }

    @Test
    @DisplayName("双轨融合：以确定性提取的年份与三表校准 LLM 提取结果")
    void testFuseWithDeterministic_CalibratesLlmRequirements() {
        FinancialRequirement llmReq = new FinancialRequirement(
                true, true, List.of("2022"), List.of("资产负债表"), true, "大模型提取的财务说明");
        FinancialRequirement detReq = new FinancialRequirement(
                true, true, List.of("2021", "2022", "2023"), List.of("资产负债表", "利润表", "现金流量表"), true, "规则提取的财务说明");

        FinancialRequirement fused = FinancialRequirement.fuseWithDeterministic(llmReq, detReq);

        assertThat(fused).isNotNull();
        assertThat(fused.requiredYears()).containsExactly("2021", "2022", "2023");
        assertThat(fused.requiredStatements()).contains("资产负债表", "利润表", "现金流量表");
        assertThat(fused.description()).isEqualTo("大模型提取的财务说明");
    }

    @Test
    @DisplayName("政府采购套话「健全的财务会计制度」不得单独抽出财务要求")
    void testExtract_WhenOnlyLegalBoilerplate_ReturnsNull() {
        String tenderText = "投标人应具有健全的财务会计制度和良好的商业信誉，有依法缴纳税收和社会保障资金的良好记录。";

        FinancialRequirement req = DeterministicFinancialExtractor.extract(tenderText);

        assertThat(req).isNull();
    }

    @Test
    @DisplayName("同一份招标既有免除短语又有须提供审计报告：冲突时按必须提供处理")
    void testExtract_WhenExemptionConflictsWithPositiveRequirement_StaysRequired() {
        String tenderText = """
                除小微企业免于提供财务状况证明外，其余投标人须提供2023年度经会计师事务所审计的财务会计报表，
                包括资产负债表、利润表、现金流量表。
                """;

        FinancialRequirement req = DeterministicFinancialExtractor.extract(tenderText);

        assertThat(req).isNotNull();
        assertThat(req.required()).isTrue();
        assertThat(req.requiredStatements()).contains("资产负债表", "利润表", "现金流量表");
    }

    @Test
    @DisplayName("只要求经审计财务报告、未点名三表：不默认塞满三张报表")
    void testExtract_WhenAuditReportOnly_DoesNotDefaultThreeStatements() {
        String tenderText = "投标人须提供经会计师事务所审计的财务报告。";

        FinancialRequirement req = DeterministicFinancialExtractor.extract(tenderText);

        assertThat(req).isNotNull();
        assertThat(req.required()).isTrue();
        assertThat(req.auditReportRequired()).isTrue();
        assertThat(req.requiredStatements()).isEmpty();
    }

    @Test
    @DisplayName("fuse 在免/要冲突时偏向必须提供")
    void testFuse_WhenExemptConflictsWithRequired_KeepsRequired() {
        FinancialRequirement exempt = FinancialRequirement.notRequired("免除");
        FinancialRequirement required = new FinancialRequirement(
                true, true, List.of("2023"), List.of("资产负债表"), true, "须提供");

        FinancialRequirement fused = FinancialRequirement.fuse(exempt, required);

        assertThat(fused.required()).isTrue();
        assertThat(fused.description()).isEqualTo("须提供");
        FinancialRequirement fusedDet = FinancialRequirement.fuseWithDeterministic(required, exempt);
        assertThat(fusedDet.required()).isTrue();
        assertThat(fusedDet.description()).isEqualTo("须提供");
    }

    @Test
    @DisplayName("「应当提交」财务报表与免除短语并存：仍按必须提供")
    void testExtract_WhenShallSubmitConflictsWithExemption_StaysRequired() {
        String tenderText = "小微企业免于提供财务状况证明。投标人应当提交近三年经审计的财务报表。";

        FinancialRequirement req = DeterministicFinancialExtractor.extract(tenderText);

        assertThat(req).isNotNull();
        assertThat(req.required()).isTrue();
    }

    @Test
    @DisplayName("双轨融合：确定性「近三年」与 LLM 绝对年份取并集")
    void testFuseWithDeterministic_UnionsRelativeAndAbsoluteYears() {
        FinancialRequirement llmReq = new FinancialRequirement(
                true, true, List.of("2021", "2022", "2023"), List.of("资产负债表"), true, "须提供三年报表");
        FinancialRequirement detReq = new FinancialRequirement(
                true, true, List.of("近三年"), List.of(), true, "近三年经审计");

        FinancialRequirement fused = FinancialRequirement.fuseWithDeterministic(llmReq, detReq);

        assertThat(fused.requiredYears()).contains("近三年", "2021", "2022", "2023");
    }
}
