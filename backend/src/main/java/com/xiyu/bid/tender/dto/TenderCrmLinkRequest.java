package com.xiyu.bid.tender.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 标讯关联CRM商机请求体。
 * 专用 DTO 避免触发完整的 @Valid TenderRequest 校验（title/deadline 可为空）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "标讯关联CRM商机请求")
public class TenderCrmLinkRequest {

    @NotBlank(message = "CRM商机ID不能为空")
    @Schema(description = "CRM商机ID", required = true)
    private String crmOpportunityId;

    @NotBlank(message = "CRM商机名称不能为空")
    @Schema(description = "CRM商机名称", required = true)
    private String crmOpportunityName;
}
