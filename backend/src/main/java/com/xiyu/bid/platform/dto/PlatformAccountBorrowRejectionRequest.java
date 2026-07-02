package com.xiyu.bid.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 平台账号借用申请驳回请求。参照 docs/architecture/approval-contract.md。
 * <p>驳回必须填写原因，{@code comment} 必填。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformAccountBorrowRejectionRequest {

    /** 驳回原因（统一字段名 comment，遵循 docs/architecture/approval-contract.md §3.2）。 */
    @NotBlank(message = "驳回原因不能为空")
    @Size(max = 500, message = "驳回原因不能超过500字")
    private String comment;
}
