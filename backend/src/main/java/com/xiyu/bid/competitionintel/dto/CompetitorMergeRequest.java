// Input: 竞品合并请求体
// Output: 校验后的合并目标 ID
// Pos: competitionintel/dto/ - 请求 DTO
package com.xiyu.bid.competitionintel.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 竞品合并请求（specs/055）。
 * <p>将当前竞品合并到 {@code targetId} 指向的竞品。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompetitorMergeRequest {

    @NotNull(message = "合并目标竞品 ID 不能为空")
    private Long targetId;
}
