package com.xiyu.bid.performance.domain.valueobject;

/**
 * 项目类型枚举（蓝图 4.5）。
 *
 * <p>"集采"的枚举名统一为 {@code COLLECTIVE}，与立项模块
 * {@link com.xiyu.bid.project.core.InitiationFieldPolicy.ProjectType} 对齐，
 * 避免前端筛选传 {@code COLLECTIVE} 时因枚举名不一致触发
 * {@code InvalidDataAccessApiUsageException}（Sentry XIYU-Y 根因）。
 */
public enum ProjectType {
    OFFICE("办公"),
    COMPREHENSIVE("综合"),
    COLLECTIVE("集采"),
    INDUSTRIAL("工业品"),
    OTHER("其他");

    private final String label;

    ProjectType(String label) {
        this.label = label;
    }

    public String displayName() {
        return label;
    }
}
