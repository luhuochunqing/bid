// Input: PATCH /form 请求体 - 项目评估表单填写
// Output: 7个字段的表单数据
// Pos: project/dto/
package com.xiyu.bid.project.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationFormUpdateRequest {

    @NotBlank(message = "项目背景不能为空")
    @Size(max = 4096, message = "项目背景最大4096字符")
    private String background;

    @NotBlank(message = "竞争对手情况不能为空")
    @Size(max = 4096, message = "竞争对手情况最大4096字符")
    private String competitors;

    @NotBlank(message = "项目合同周期不能为空")
    @Size(max = 64, message = "项目合同周期最大64字符")
    private String contractPeriod;

    @NotNull(message = "入围家数不能为空")
    @Min(value = 1, message = "入围家数最少为1")
    @Max(value = 999, message = "入围家数最多为999")
    private Integer shortlistedBidders;

    @NotNull(message = "平台服务费不能为空")
    @DecimalMin(value = "0", message = "平台服务费不能为负数")
    @DecimalMax(value = "9999999999.99", message = "平台服务费超出范围")
    private BigDecimal platformFee;

    private String previousBid;

    private Boolean recommendation;
}
