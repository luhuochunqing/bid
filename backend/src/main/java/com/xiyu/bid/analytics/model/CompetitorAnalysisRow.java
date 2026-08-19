// Input: 竞品分析 JPQL 查询结果行
// Output: 竞品行承载 record（含 notes 是否中标枚举）
// Pos: model/ - 数据承载层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.analytics.model;

import com.xiyu.bid.entity.Project;

import java.time.LocalDateTime;

public record CompetitorAnalysisRow(
    Long projectId,
    String projectName,
    String competitorName,
    String discount,
    String tenderEntity,
    String paymentTerm,
    String notes,
    String resultType,
    Project.Status projectStatus,
    LocalDateTime createdAt
) {
}
