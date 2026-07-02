package com.xiyu.bid.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request DTO for rejecting a borrow application. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RejectRequest {

    /**
     * 驳回原因（统一字段名 comment，遵循 docs/architecture/approval-contract.md §3.2）
     */
    @NotBlank(message = "驳回原因不能为空")
    @Size(max = 500, message = "驳回原因不能超过500字")
    private String comment;
}
