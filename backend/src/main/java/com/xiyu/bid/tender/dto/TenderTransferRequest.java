package com.xiyu.bid.tender.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 标讯转派请求 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenderTransferRequest {

    /**
     * 新项目负责人用户 ID。
     */
    @NotNull(message = "新项目负责人不能为空")
    private Long newOwnerId;
}
