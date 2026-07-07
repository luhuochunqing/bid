package com.xiyu.bid.performance.application.service;

import com.xiyu.bid.performance.domain.valueobject.ProjectType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 业绩枚举中文映射单元测试。
 *
 * <p>Sentry XIYU-Y 修复回归：V1151 迁移后 project_type='COLLECTIVE' 仍必须
 * 映射为 "集采"，不得返回英文枚举名。原 PR !1830 漏改 projectType(String) 的
 * 反向映射分支，导致导出 Excel/ZIP 时显示 "COLLECTIVE" 而非 "集采"。
 */
class PerformanceEnumLabelsTest {

    @Test
    void projectType_collective_shouldMapToJiCai() {
        assertThat(PerformanceEnumLabels.projectType("COLLECTIVE"))
            .as("V1151 迁移后 'COLLECTIVE' 必须映射为 '集采'")
            .isEqualTo("集采");
    }

    @Test
    void projectType_collectiveEnumName_shouldMapToJiCai() {
        // 模拟导出时 r.projectType().name() 的调用路径
        assertThat(PerformanceEnumLabels.projectType(ProjectType.COLLECTIVE.name()))
            .isEqualTo("集采");
    }

    @Test
    void projectType_centralizedKeptAsAliasForRollback() {
        // V1151 回滚期间 DB 短暂出现 CENTRALIZED，必须继续映射为 "集采"
        assertThat(PerformanceEnumLabels.projectType("CENTRALIZED"))
            .isEqualTo("集采");
    }

    @Test
    void projectType_otherEnums_unchanged() {
        assertThat(PerformanceEnumLabels.projectType("OFFICE")).isEqualTo("办公");
        assertThat(PerformanceEnumLabels.projectType("COMPREHENSIVE")).isEqualTo("综合");
        assertThat(PerformanceEnumLabels.projectType("INDUSTRIAL")).isEqualTo("工业品");
        assertThat(PerformanceEnumLabels.projectType("OTHER")).isEqualTo("其他");
    }

    @Test
    void projectType_nullAndUnknown() {
        assertThat(PerformanceEnumLabels.projectType(null)).isEmpty();
        assertThat(PerformanceEnumLabels.projectType("UNKNOWN_VALUE")).isEqualTo("UNKNOWN_VALUE");
    }

    @Test
    void parseProjectType_jiCai_shouldMapToCollective() {
        // 反向映射也应正确（原 PR 已修复，此处做对称性回归）
        assertThat(PerformanceEnumLabels.parseProjectType("集采"))
            .isEqualTo(ProjectType.COLLECTIVE);
    }
}
