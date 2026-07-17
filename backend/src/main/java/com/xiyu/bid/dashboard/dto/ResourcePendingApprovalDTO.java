package com.xiyu.bid.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 工作台角色化改造：资源待审批聚合 DTO（spec.md §3 模块4）。
 * 合并账户借用申请和 CA 借用申请，统一展示格式。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourcePendingApprovalDTO {

    /** 申请类型："ACCOUNT"（账户借用）或 "CA"（CA 借用） */
    private String applicationType;

    /** 申请记录 ID（对应各自实体的 id） */
    private Long applicationId;

    /** 资源标签：账户名 或 CA 显示名 */
    private String resourceLabel;

    /** 申请人 ID */
    private Long applicantId;

    /** 申请人姓名 */
    private String applicantName;

    /** 申请用途 */
    private String purpose;

    /** 关联项目 ID */
    private Long projectId;

    /** 关联项目名称 */
    private String projectName;

    /** 申请创建时间（用于排序展示） */
    private LocalDateTime createdAt;
}
