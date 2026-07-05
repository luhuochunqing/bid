// Input: Service 层转移结果
// Output: HTTP 响应体 JSON
// Pos: project/dto/ - 协议适配
// 维护声明: 仅维护响应字段绑定；不含业务规则。

package com.xiyu.bid.project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 项目转移响应 DTO。
 * <p>对应 FR-001：POST /api/projects/{projectId}/transfer 成功响应。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectTransferResponse {

    /** 项目 ID */
    private Long projectId;

    /** 项目名称 */
    private String projectName;

    /** 原负责人用户 ID */
    private Long oldOwnerUserId;

    /** 原负责人姓名 */
    private String oldOwnerName;

    /** 新负责人用户 ID */
    private Long newOwnerUserId;

    /** 新负责人姓名 */
    private String newOwnerName;

    /** 转移时间 */
    private LocalDateTime transferredAt;

    /** 是否同步更新了关联标讯 */
    private Boolean tenderUpdated;

    /** 关联标讯 ID（若存在） */
    private Long tenderId;
}
