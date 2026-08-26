// Input: 竞品 upsert 请求体
// Output: 校验后的竞品名称 + 可选情报字段
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
 * <p>录入端只传 name；竞争情报侧可附可选情报字段（industry/strengths/weaknesses）。
 * 幂等语义：name 已存在时返回已有竞品（可选字段不覆盖）；不存在时带字段创建。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompetitorUpsertRequest {

    @NotBlank(message = "竞品名称不能为空")
    @Size(max = 200, message = "竞品名称不能超过 200 字符")
    private String name;

    @Size(max = 100, message = "行业不能超过 100 字符")
    private String industry;

    @Size(max = 5000, message = "优势不能超过 5000 字符")
    private String strengths;

    @Size(max = 5000, message = "劣势不能超过 5000 字符")
    private String weaknesses;
}
