// Input: 标书审核驳回 HTTP 请求体
// Output: 驳回入参（驳回原因必填）
// Pos: project/dto/
package com.xiyu.bid.project.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 标书审核驳回请求。参照 docs/architecture/approval-contract.md。
 * <p>驳回必须填写原因，{@code comment} 必填。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DraftingRejectionRequest {
    @NotBlank(message = "驳回原因不能为空")
    private String comment;
}
