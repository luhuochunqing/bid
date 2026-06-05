package com.xiyu.bid.ai.core;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectScorePreviewPolicyTest {

    @Test
    void evaluate_shouldBuildExpectedScoreAndTasks() {
        ProjectScorePreviewPolicy.PreviewResult result = ProjectScorePreviewPolicy.evaluate(
            new ProjectScorePreviewPolicy.PreviewInput(
                "智慧城市 IOC 项目",
                "政府",
                new BigDecimal("680.00"),
                List.of("信创", "智慧城市"),
                11L,
                22L
            )
        );

        assertThat(result.winScore()).isEqualTo(75);
        assertThat(result.winLevel()).isEqualTo("medium");
        assertThat(result.scoreCategories()).extracting(ProjectScorePreviewPolicy.CategoryCoverage::name)
            .containsExactly("技术", "商务", "案例", "服务");
        assertThat(result.scoreCategories().get(0).gaps()).containsExactly("大数据平台");
        assertThat(result.gapItems()).hasSize(2);
        assertThat(result.risks()).hasSize(2);
        assertThat(result.suggestions()).containsExactly(
            "优先补充关键评分点材料，先处理高权重项",
            "突出国产化兼容和信创生态证明材料"
        );
        assertThat(result.generatedTasks()).extracting(ProjectScorePreviewPolicy.GeneratedTask::name)
            .containsExactly("补齐大数据平台", "补齐运维承诺");
    }

    @Test
    void resolveGapRequirement_shouldReturnKnownRequirementText() {
        assertThat(ProjectScorePreviewPolicy.resolveGapRequirement("物联网架构方案")).isEqualTo("架构图+技术说明");
        assertThat(ProjectScorePreviewPolicy.resolveGapRequirement("未知项")).isEqualTo("补充相关证明材料");
    }
}
