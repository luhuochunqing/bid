// Input: 标书审核通过 HTTP 请求体
// Output: 审核入参（审批意见可选）
// Pos: project/dto/
package com.xiyu.bid.project.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 标书审核通过请求。参照 docs/architecture/approval-contract.md。
 * <p>通过操作允许不填意见，{@code comment} 可为空。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DraftingApprovalRequest {
    /** 审批意见（可选，通过操作允许不填）。 */
    @Size(max = 500, message = "审批意见不能超过500字")
    private String comment;
}
