// Input: 更新后的项目实体（projectId / name / updatedAt）
// Output: 接口六成功响应 data 段
// Pos: integration/external/ - 对外集成接口契约
package com.xiyu.bid.integration.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 接口六：项目修改（v3.12 新增）成功响应（HTTP 200）data 段。
 *
 * <p>示例：{@code {"projectId": 123, "projectName": "...", "updatedAt": "2026-08-19 15:30"}}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectUpdateResponse {

    /** 项目 ID。 */
    private Long projectId;

    /** 项目名称（更新后现值）。 */
    private String projectName;

    /** 更新时间，格式 yyyy-MM-dd HH:mm（对齐接口文档示例）。 */
    private String updatedAt;
}
