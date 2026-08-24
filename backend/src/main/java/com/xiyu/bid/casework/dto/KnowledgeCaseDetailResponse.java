package com.xiyu.bid.casework.dto;

import java.time.LocalDateTime;

/**
 * 案例详情响应（PRD 案例库 2.3.3）：实体全量字段 + 招标主体。
 *
 * <p>招标主体（ownerUnit）通过 source_project_id 关联
 * project_initiation_details.owner_unit 查询，关联不到时为 null。</p>
 */
public record KnowledgeCaseDetailResponse(
        Long id,
        Long sourceProjectId,
        String sourceProjectName,
        String ownerUnit,
        String scoringPointTitle,
        Long scoringPointId,
        String requirementRaw,
        String responseText,
        Integer reuseCount,
        Boolean pinned,
        String status,
        String customerType,
        String projectType,
        String bidResult,
        String scoringCategory,
        String productLine,
        LocalDateTime createdAt
) {
}
