package com.xiyu.bid.casework.domain.model;

public record CaseExportRecord(
        String scoringPointTitle,
        String sourceProjectName,
        String projectType,
        String customerType,
        String scoringCategory,
        String bidResult,
        int reuseCount,
        String createdAt,
        String responseSummary,
        String sourceSectionTitle,
        String sourceDocumentName,
        Integer sourcePageNumber
) {
    public static final String[] HEADERS = {
            "评分项标题",
            "来源项目",
            "项目类型",
            "客户类型",
            "评分类别",
            "中标结果",
            "复用次数",
            "创建时间",
            "应答摘要",
            "原文所在标题",
            "原文所在文档",
            "原文页码"
    };

    /**
     * 向后兼容的 9 参数构造函数。
     */
    public CaseExportRecord(
            String scoringPointTitle,
            String sourceProjectName,
            String projectType,
            String customerType,
            String scoringCategory,
            String bidResult,
            int reuseCount,
            String createdAt,
            String responseSummary
    ) {
        this(scoringPointTitle, sourceProjectName, projectType, customerType,
                scoringCategory, bidResult, reuseCount, createdAt, responseSummary,
                null, null, null);
    }

    public String[] toRowValues() {
        return new String[]{
                scoringPointTitle,
                sourceProjectName,
                projectType,
                customerType,
                scoringCategory,
                bidResult,
                String.valueOf(reuseCount),
                createdAt,
                responseSummary,
                sourceSectionTitle,
                sourceDocumentName,
                sourcePageNumber == null ? null : String.valueOf(sourcePageNumber)
        };
    }
}
