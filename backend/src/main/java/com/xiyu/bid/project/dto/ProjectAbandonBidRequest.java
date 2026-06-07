// Input: 弃标原因字符串
// Output: 弃标申请请求
// Pos: project/dto/
package com.xiyu.bid.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectAbandonBidRequest {

    @NotBlank(message = "弃标原因不能为空")
    @Size(max = 1000, message = "弃标原因不能超过1000字")
    private String reason;
}
