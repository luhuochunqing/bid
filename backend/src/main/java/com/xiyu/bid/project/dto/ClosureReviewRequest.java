// Input: 结项审核 HTTP 请求体（蓝图 §3.3.1.6 - 项目结项审核流程）
// Output: 审核入参（审核意见）
// Pos: project/dto/
package com.xiyu.bid.project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClosureReviewRequest {
    /** 审核意见（可选）。 */
    private String comment;
}
