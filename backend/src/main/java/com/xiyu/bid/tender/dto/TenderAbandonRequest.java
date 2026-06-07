// Input: 弃标原因字符串
// Output: 弃标申请请求
// Pos: tender/dto/
package com.xiyu.bid.tender.dto;

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
public class TenderAbandonRequest {

    @NotBlank(message = "弃标原因不能为空")
    @Size(max = 500, message = "弃标原因不能超过500字")
    private String reason;
}
