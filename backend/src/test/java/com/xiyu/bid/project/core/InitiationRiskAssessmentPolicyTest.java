package com.xiyu.bid.project.core;

import com.xiyu.bid.project.core.InitiationRiskAssessmentPolicy.Result;
import com.xiyu.bid.project.dto.CustomerInfoRow;
import com.xiyu.bid.tender.core.BidRiskLevelPolicy.RiskLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 立项 AI 风险评估策略单元测试（FP-Java 纯核心）。
 *
 * <p>覆盖蓝图规则（与 BidRiskLevelPolicy 一致）：
 * <ol>
 *   <li>任何关键人倾向性=反对 → HIGH</li>
 *   <li>最高决策人=支持 + 3 个其他关键人=支持 → LOW</li>
 *   <li>其他 → MEDIUM</li>
 * </ol>
 *
 * <p>角色识别兼容 position 数字代码（'1'=最高决策人, '9'/'10'/'11'=其他关键决策人）
 * 和 role 中文标签两种存储形态。
 * <p>倾向性值兼容数字代码（1=支持/2=中立/3=反对）和中文（支持/中立/反对）。
 */
@DisplayName("InitiationRiskAssessmentPolicy - 立项 AI 风险评估")
class InitiationRiskAssessmentPolicyTest {

    @Nested
    @DisplayName("规则1：任何关键人反对 → HIGH")
    class AnyOpposed {

        @Test
        @DisplayName("最高决策人反对（position 代码）→ HIGH，说明提及最高决策人")
        void shouldReturnHigh_whenHighestDecisionMakerOpposed_byPosition() {
            List<CustomerInfoRow> rows = List.of(
                    row("1", null, "3"), // 最高决策人反对
                    row("9", null, "1"),
                    row("10", null, "1")
            );
            Result result = InitiationRiskAssessmentPolicy.evaluate(rows);
            assertThat(result.riskLevel()).isEqualTo(RiskLevel.HIGH);
            assertThat(result.notes()).contains("最高决策人反对");
        }

        @Test
        @DisplayName("最高决策人反对（role 中文标签）→ HIGH")
        void shouldReturnHigh_whenHighestDecisionMakerOpposed_byRole() {
            List<CustomerInfoRow> rows = List.of(
                    row(null, "项目最高决策人", "反对"),
                    row(null, "其他关键决策人1", "支持")
            );
            Result result = InitiationRiskAssessmentPolicy.evaluate(rows);
            assertThat(result.riskLevel()).isEqualTo(RiskLevel.HIGH);
            assertThat(result.notes()).contains("最高决策人反对");
        }

        @Test
        @DisplayName("任一其他关键人反对 → HIGH，说明提及其他关键决策人")
        void shouldReturnHigh_whenAnyOtherKeyDecisionMakerOpposed() {
            List<CustomerInfoRow> rows = List.of(
                    row("1", null, "1"), // 最高决策人支持
                    row("9", null, "1"),
                    row("10", null, "3"), // 其他关键人2反对
                    row("11", null, "1")
            );
            Result result = InitiationRiskAssessmentPolicy.evaluate(rows);
            assertThat(result.riskLevel()).isEqualTo(RiskLevel.HIGH);
            assertThat(result.notes()).contains("其他关键决策人反对");
        }

        @Test
        @DisplayName("最高决策人和其他关键人均反对 → HIGH，说明同时提及")
        void shouldReturnHigh_whenBothHighestAndOtherOpposed() {
            List<CustomerInfoRow> rows = List.of(
                    row("1", null, "3"),
                    row("9", null, "3")
            );
            Result result = InitiationRiskAssessmentPolicy.evaluate(rows);
            assertThat(result.riskLevel()).isEqualTo(RiskLevel.HIGH);
            assertThat(result.notes()).contains("最高决策人及其他关键决策人均存在反对");
        }
    }

    @Nested
    @DisplayName("规则2：最高决策人支持 + 3个其他关键人支持 → LOW")
    class AllSupported {

        @Test
        @DisplayName("最高决策人支持 + 3个其他关键人支持 → LOW")
        void shouldReturnLow_whenHighestAndThreeOthersSupport() {
            List<CustomerInfoRow> rows = List.of(
                    row("1", null, "1"),
                    row("9", null, "1"),
                    row("10", null, "1"),
                    row("11", null, "1")
            );
            Result result = InitiationRiskAssessmentPolicy.evaluate(rows);
            assertThat(result.riskLevel()).isEqualTo(RiskLevel.LOW);
            assertThat(result.notes()).contains("项目风险较低");
        }

        @Test
        @DisplayName("最高决策人支持 + 仅2个其他关键人支持 → MEDIUM（不足3个）")
        void shouldReturnMedium_whenOnlyTwoOthersSupport() {
            List<CustomerInfoRow> rows = List.of(
                    row("1", null, "1"),
                    row("9", null, "1"),
                    row("10", null, "1"),
                    row("11", null, "2") // 中立
            );
            Result result = InitiationRiskAssessmentPolicy.evaluate(rows);
            assertThat(result.riskLevel()).isEqualTo(RiskLevel.MEDIUM);
        }
    }

    @Nested
    @DisplayName("规则3：其他场景 → MEDIUM")
    class OtherCases {

        @Test
        @DisplayName("所有人中立 → MEDIUM")
        void shouldReturnMedium_whenAllNeutral() {
            List<CustomerInfoRow> rows = List.of(
                    row("1", null, "2"),
                    row("9", null, "2"),
                    row("10", null, "2")
            );
            Result result = InitiationRiskAssessmentPolicy.evaluate(rows);
            assertThat(result.riskLevel()).isEqualTo(RiskLevel.MEDIUM);
            assertThat(result.notes()).contains("项目风险中等");
        }

        @Test
        @DisplayName("空列表 → MEDIUM（降级，不报错）")
        void shouldReturnMedium_whenEmptyRows() {
            Result result = InitiationRiskAssessmentPolicy.evaluate(List.of());
            assertThat(result.riskLevel()).isEqualTo(RiskLevel.MEDIUM);
            assertThat(result.notes()).isNotBlank();
        }

        @Test
        @DisplayName("null 输入 → MEDIUM（降级，不报错）")
        void shouldReturnMedium_whenNullRows() {
            Result result = InitiationRiskAssessmentPolicy.evaluate(null);
            assertThat(result.riskLevel()).isEqualTo(RiskLevel.MEDIUM);
            assertThat(result.notes()).isNotBlank();
        }
    }

    @Nested
    @DisplayName("角色识别兼容性")
    class RoleIdentification {

        @Test
        @DisplayName("position='1' 识别为最高决策人")
        void shouldIdentifyHighest_byPosition() {
            List<CustomerInfoRow> rows = List.of(
                    row("1", "任意角色", "3")
            );
            assertThat(InitiationRiskAssessmentPolicy.evaluate(rows).riskLevel())
                    .isEqualTo(RiskLevel.HIGH);
        }

        @Test
        @DisplayName("role 含'最高决策人' 识别为最高决策人（即使 position 不为 1）")
        void shouldIdentifyHighest_byRole() {
            List<CustomerInfoRow> rows = List.of(
                    row(null, "项目最高决策人", "3")
            );
            assertThat(InitiationRiskAssessmentPolicy.evaluate(rows).riskLevel())
                    .isEqualTo(RiskLevel.HIGH);
        }

        @Test
        @DisplayName("position='9'/'10'/'11' 识别为其他关键决策人")
        void shouldIdentifyOtherKey_byPosition() {
            List<CustomerInfoRow> rows = List.of(
                    row("1", null, "1"),
                    row("9", null, "3") // 其他关键人1反对
            );
            assertThat(InitiationRiskAssessmentPolicy.evaluate(rows).riskLevel())
                    .isEqualTo(RiskLevel.HIGH);
        }

        @Test
        @DisplayName("role 含'其他关键决策人' 识别为其他关键决策人")
        void shouldIdentifyOtherKey_byRole() {
            List<CustomerInfoRow> rows = List.of(
                    row(null, "项目最高决策人", "1"),
                    row(null, "其他关键决策人2", "3")
            );
            assertThat(InitiationRiskAssessmentPolicy.evaluate(rows).riskLevel())
                    .isEqualTo(RiskLevel.HIGH);
        }

        @Test
        @DisplayName("非关键人角色（如专家）的倾向性不参与判定")
        void shouldIgnoreNonKeyDecisionMakers() {
            List<CustomerInfoRow> rows = List.of(
                    row("1", null, "1"), // 最高决策人支持
                    row("9", null, "1"),
                    row("10", null, "1"),
                    row("11", null, "1"),
                    row("12", "专家1", "3") // 专家反对，不参与判定
            );
            Result result = InitiationRiskAssessmentPolicy.evaluate(rows);
            assertThat(result.riskLevel()).isEqualTo(RiskLevel.LOW);
        }
    }

    @Nested
    @DisplayName("倾向性值兼容性")
    class TendencyValueCompat {

        @Test
        @DisplayName("中文值（支持/中立/反对）正常判定")
        void shouldHandleChineseValues() {
            List<CustomerInfoRow> rows = List.of(
                    row("1", null, "反对"),
                    row("9", null, "支持")
            );
            assertThat(InitiationRiskAssessmentPolicy.evaluate(rows).riskLevel())
                    .isEqualTo(RiskLevel.HIGH);
        }

        @Test
        @DisplayName("数字代码（1/2/3）正常判定")
        void shouldHandleNumericCodes() {
            List<CustomerInfoRow> rows = List.of(
                    row("1", null, "1"), // 支持
                    row("9", null, "1"),
                    row("10", null, "1"),
                    row("11", null, "1")
            );
            assertThat(InitiationRiskAssessmentPolicy.evaluate(rows).riskLevel())
                    .isEqualTo(RiskLevel.LOW);
        }
    }

    /** 构造测试用 CustomerInfoRow，只设置 position/role/preference。 */
    private static CustomerInfoRow row(String position, String role, String preference) {
        return CustomerInfoRow.builder()
                .position(position)
                .role(role)
                .preference(preference)
                .build();
    }
}
