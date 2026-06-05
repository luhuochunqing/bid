// Input: TechnicalSubTypePolicy（classify + classifyAll 方法）
// Output: 技术要点子类型分类行为验证
// Pos: Test/biddraftagent/domain/technical

package com.xiyu.bid.biddraftagent.domain.technical;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TechnicalSubTypePolicyTest {

    private final TechnicalSubTypePolicy policy = new TechnicalSubTypePolicy();

    // ── classify() 单元测试 ───────────────────────────────────────────────

    @Test
    void classify_hardIndex_withNumericSpec() {
        assertThat(policy.classify("CPU主频≥2.0GHz"))
                .isEqualTo(TechnicalSubType.HARD_INDEX);
    }

    @Test
    void classify_hardIndex_withMemorySpec() {
        assertThat(policy.classify("内存不低于32GB"))
                .isEqualTo(TechnicalSubType.HARD_INDEX);
    }

    @Test
    void classify_hardIndex_withConcurrency() {
        assertThat(policy.classify("支持≥5000并发用户"))
                .isEqualTo(TechnicalSubType.HARD_INDEX);
    }

    @Test
    void classify_compatibility_withLinux() {
        assertThat(policy.classify("兼容国产Linux操作系统"))
                .isEqualTo(TechnicalSubType.COMPATIBILITY);
    }

    @Test
    void classify_compatibility_withCrossPlatform() {
        assertThat(policy.classify("支持跨平台部署"))
                .isEqualTo(TechnicalSubType.COMPATIBILITY);
    }

    @Test
    void classify_compatibility_withIntegration() {
        assertThat(policy.classify("需与现有OA系统对接"))
                .isEqualTo(TechnicalSubType.COMPATIBILITY);
    }

    @Test
    void classify_bonus_withPriority() {
        assertThat(policy.classify("具有同类项目经验优先"))
                .isEqualTo(TechnicalSubType.BONUS);
    }

    @Test
    void classify_bonus_withCertPreference() {
        assertThat(policy.classify("拥有CMMI5认证者优先"))
                .isEqualTo(TechnicalSubType.BONUS);
    }

    @Test
    void classify_functional_asDefault() {
        assertThat(policy.classify("提供用户管理功能"))
                .isEqualTo(TechnicalSubType.FUNCTIONAL);
    }

    @Test
    void classify_numericHardIndex() {
        assertThat(policy.classify("存储容量≥100TB"))
                .isEqualTo(TechnicalSubType.HARD_INDEX);
    }

    @Test
    void classify_null_returnsFunctional() {
        assertThat(policy.classify(null)).isEqualTo(TechnicalSubType.FUNCTIONAL);
    }

    @Test
    void classify_blank_returnsFunctional() {
        assertThat(policy.classify("  ")).isEqualTo(TechnicalSubType.FUNCTIONAL);
    }

    // ── classifyAll() 集成测试 ────────────────────────────────────────────

    @Test
    void classifyAll_mixedRequirements() {
        List<String> requirements = List.of(
                "CPU主频≥2.0GHz",
                "兼容国产操作系统",
                "提供可视化报表功能",
                "具有同类项目实施经验优先"
        );

        List<TechnicalRequirementItem> items = policy.classifyAll(requirements);

        assertThat(items).hasSize(4);
        assertThat(items.get(0).subType()).isEqualTo(TechnicalSubType.HARD_INDEX);
        assertThat(items.get(1).subType()).isEqualTo(TechnicalSubType.COMPATIBILITY);
        assertThat(items.get(2).subType()).isEqualTo(TechnicalSubType.FUNCTIONAL);
        assertThat(items.get(3).subType()).isEqualTo(TechnicalSubType.BONUS);
    }

    @Test
    void classifyAll_empty_returnsEmpty() {
        assertThat(policy.classifyAll(List.of())).isEmpty();
    }

    @Test
    void classifyAll_null_returnsEmpty() {
        assertThat(policy.classifyAll(null)).isEmpty();
    }
}
