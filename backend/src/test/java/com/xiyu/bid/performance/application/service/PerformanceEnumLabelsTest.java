package com.xiyu.bid.performance.application.service;

import com.xiyu.bid.performance.domain.valueobject.ProjectType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 业绩枚举映射单元测试。
 *
 * <p>Sentry XIYU-Y 修复回归：V1151 迁移后 project_type='COLLECTIVE' 仍必须
 * 映射为 "集采"，不得返回英文枚举名。
 *
 * <p>正向映射（枚举 → 中文）已统一使用各枚举的 {@code displayName()} 方法，
 * 正向映射函数已从 {@link PerformanceEnumLabels} 删除；本测试改为验证
 * {@link ProjectType#displayName()} 的回归正确性。
 */
class PerformanceEnumLabelsTest {

    @Test
    void displayName_collective_shouldMapToJiCai() {
        assertThat(ProjectType.COLLECTIVE.displayName())
            .as("V1151 迁移后 'COLLECTIVE' 必须映射为 '集采'")
            .isEqualTo("集采");
    }

    @Test
    void displayName_otherEnums_unchanged() {
        assertThat(ProjectType.OFFICE.displayName()).isEqualTo("办公");
        assertThat(ProjectType.COMPREHENSIVE.displayName()).isEqualTo("综合");
        assertThat(ProjectType.INDUSTRIAL.displayName()).isEqualTo("工业品");
        assertThat(ProjectType.OTHER.displayName()).isEqualTo("其他");
    }

    @Test
    void parseProjectType_jiCai_shouldMapToCollective() {
        // 反向映射也应正确（原 PR 已修复，此处做对称性回归）
        assertThat(PerformanceEnumLabels.parseProjectType("集采"))
            .isEqualTo(ProjectType.COLLECTIVE);
    }
}
