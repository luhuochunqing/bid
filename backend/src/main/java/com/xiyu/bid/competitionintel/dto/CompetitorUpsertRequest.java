// Input: 竞品 upsert 请求体
// Output: 校验后的竞品名称
// Pos: competitionintel/dto/ - 请求 DTO
package com.xiyu.bid.competitionintel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 竞品幂等 upsert 请求（specs/055）。
 * <p>录入端/改名复用：只传名称，其余字段由服务端补默认值。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompetitorUpsertRequest {

    @NotBlank(message = "竞品名称不能为空")
    @Size(max = 200, message = "竞品名称不能超过 200 字符")
    private String name;
}
