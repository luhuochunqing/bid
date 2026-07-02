// Input: 结项审核驳回 HTTP 请求体（蓝图 §3.3.1.6 - 项目结项审核流程）
// Output: 驳回入参（驳回原因必填）
// Pos: project/dto/
package com.xiyu.bid.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 结项审核驳回请求。参照 docs/architecture/approval-contract.md。
 * <p>驳回必须填写原因，{@code comment} 必填。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClosureRejectionRequest {

    /** 驳回原因（必填）。 */
    @NotBlank(message = "驳回原因不能为空")
    @Size(max = 500, message = "驳回原因不能超过500字")
    private String comment;
}
