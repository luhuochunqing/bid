package com.xiyu.bid.tender.core;

import com.xiyu.bid.tender.core.BidRiskLevelPolicy.RiskLevel;
import com.xiyu.bid.tender.core.BidRiskLevelPolicy.RiskLevelInput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 投标风险等级判定策略单元测试（FP-Java 纯核心）。
 *
 * <p>覆盖蓝图规则：
 * <ol>
 *   <li>任何关键人 TENDENCY=反对 → HIGH</li>
 *   <li>最高决策人=支持 + 3 其他关键人=支持 → LOW</li>
 *   <li>其他 → MEDIUM</li>
 * </ol>
 *
 * <p>支持中文（支持/中立/反对）与真实入库的数字代码（1/2/3）。
 */
@DisplayName("BidRiskLevelPolicy - 投标风险等级判定")
class BidRiskLevelPolicyTest {

    @Nested
    @DisplayName("规则1：任何关键人反对 → HIGH")
    class AnyOpposed {

        @Test
        @DisplayName("最高决策人反对 → HIGH")
        void shouldReturnHigh_whenHighestDecisionMakerOpposed() {
            RiskLevelInput input = new RiskLevelInput(
                "反对",
                List.of("支持", "支持", "支持")
            );
            assertThat(BidRiskLevelPolicy.evaluate(input)).isEqualTo(RiskLevel.HIGH);
        }

        @Test
        @DisplayName("任一其他关键人反对 → HIGH（即使最高决策人支持）")
        void shouldReturnHigh_whenAnyOtherKeyDecisionMakerOpposed() {
            RiskLevelInput input = new RiskLevelInput(
                "支持",
                List.of("支持", "反对", "支持")
            );
            assertThat(BidRiskLevelPolicy.evaluate(input)).isEqualTo(RiskLevel.HIGH);
        }

        @Test
        @DisplayName("数字代码：任一其他关键人=3 → HIGH")
        void shouldReturnHigh_whenAnyOtherKeyDecisionMakerHasOpposeCode() {
            RiskLevelInput input = new RiskLevelInput(
                "1",
                List.of("1", "3", "1")
            );
            assertThat(BidRiskLevelPolicy.evaluate(input)).isEqualTo(RiskLevel.HIGH);
        }
    }

    @Nested
    @DisplayName("规则2：最高决策人支持 + 3 其他关键人支持 → LOW")
    class HighestSupportedAndThreeOthersSupported {

        @Test
        @DisplayName("最高决策人=支持 + 3 其他关键人=支持 → LOW")
        void shouldReturnLow_whenHighestSupportedAndThreeOthersSupported() {
            RiskLevelInput input = new RiskLevelInput(
                "支持",
                List.of("支持", "支持", "支持")
            );
            assertThat(BidRiskLevelPolicy.evaluate(input)).isEqualTo(RiskLevel.LOW);
        }

        @Test
        @DisplayName("数字代码：最高决策人=1 + 3 其他关键人=1 → LOW")
        void shouldReturnLow_whenAllAreSupportCode() {
            RiskLevelInput input = new RiskLevelInput(
                "1",
                List.of("1", "1", "1")
            );
            assertThat(BidRiskLevelPolicy.evaluate(input)).isEqualTo(RiskLevel.LOW);
        }

        @Test
        @DisplayName("4 个其他关键人=支持（超过 3 人）→ 仍为 LOW")
        void shouldReturnLow_whenMoreThanThreeOthersSupported() {
            RiskLevelInput input = new RiskLevelInput(
                "支持",
                List.of("支持", "支持", "支持", "支持")
            );
            assertThat(BidRiskLevelPolicy.evaluate(input)).isEqualTo(RiskLevel.LOW);
        }
    }

    @Nested
    @DisplayName("规则3：其他场景 → MEDIUM")
    class OtherCasesMedium {

        @Test
        @DisplayName("最高决策人=支持 + 仅 2 其他关键人=支持 → MEDIUM（不满 3 人）")
        void shouldReturnMedium_whenHighestSupportedButOnlyTwoOthersSupported() {
            RiskLevelInput input = new RiskLevelInput(
                "支持",
                List.of("支持", "支持", "中立")
            );
            assertThat(BidRiskLevelPolicy.evaluate(input)).isEqualTo(RiskLevel.MEDIUM);
        }

        @Test
        @DisplayName("最高决策人=中立 + 3 其他关键人=支持 → MEDIUM（最高决策人未支持）")
        void shouldReturnMedium_whenHighestNeutralEvenIfThreeOthersSupported() {
            RiskLevelInput input = new RiskLevelInput(
                "中立",
                List.of("支持", "支持", "支持")
            );
            assertThat(BidRiskLevelPolicy.evaluate(input)).isEqualTo(RiskLevel.MEDIUM);
        }

        @Test
        @DisplayName("最高决策人=支持 + 其他关键人全部中立 → MEDIUM")
        void shouldReturnMedium_whenHighestSupportedButOthersNeutral() {
            RiskLevelInput input = new RiskLevelInput(
                "支持",
                List.of("中立", "中立", "中立")
            );
            assertThat(BidRiskLevelPolicy.evaluate(input)).isEqualTo(RiskLevel.MEDIUM);
        }

        @Test
        @DisplayName("所有关键人=中立 → MEDIUM")
        void shouldReturnMedium_whenAllNeutral() {
            RiskLevelInput input = new RiskLevelInput(
                "中立",
                List.of("中立", "中立", "中立")
            );
            assertThat(BidRiskLevelPolicy.evaluate(input)).isEqualTo(RiskLevel.MEDIUM);
        }
    }

    @Nested
    @DisplayName("空数据/降级场景 → MEDIUM")
    class EmptyAndDegraded {

        @Test
        @DisplayName("无 TENDENCY 数据（最高决策人=null，其他=空列表）→ MEDIUM")
        void shouldReturnMedium_whenNoTendencyData() {
            RiskLevelInput input = new RiskLevelInput(null, List.of());
            assertThat(BidRiskLevelPolicy.evaluate(input)).isEqualTo(RiskLevel.MEDIUM);
        }

        @Test
        @DisplayName("空输入（input=null）→ MEDIUM")
        void shouldReturnMedium_whenInputIsNull() {
            assertThat(BidRiskLevelPolicy.evaluate(null)).isEqualTo(RiskLevel.MEDIUM);
        }

        @Test
        @DisplayName("其他关键人列表=null → 仍按最高决策人判定（这里中立 → MEDIUM）")
        void shouldReturnMedium_whenOtherListIsNull() {
            RiskLevelInput input = new RiskLevelInput("中立", null);
            assertThat(BidRiskLevelPolicy.evaluate(input)).isEqualTo(RiskLevel.MEDIUM);
        }

        @Test
        @DisplayName("TENDENCY 值含空白/大小写不一致 → 正确归一化（支持带空格大写）")
        void shouldNormalizeTendencyValue() {
            RiskLevelInput input = new RiskLevelInput(
                "  支持  ",
                List.of(" SUPPORT ", "支持", "支持")
            );
            assertThat(BidRiskLevelPolicy.evaluate(input)).isEqualTo(RiskLevel.LOW);
        }

        @Test
        @DisplayName("TENDENCY 值无法识别（如 'unknown'）→ 视为 null（MEDIUM）")
        void shouldReturnMedium_whenTendencyUnrecognized() {
            RiskLevelInput input = new RiskLevelInput(
                "unknown",
                List.of("支持", "支持", "支持")
            );
            assertThat(BidRiskLevelPolicy.evaluate(input)).isEqualTo(RiskLevel.MEDIUM);
        }
    }
}
