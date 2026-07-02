package com.xiyu.bid.platform.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 平台账号借用申请审批通过请求。参照 docs/architecture/approval-contract.md。
 * <p>通过操作允许不填意见，{@code comment} 可为空。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformAccountBorrowApprovalRequest {

    /** 审批意见（可选，通过操作允许不填）。 */
    @Size(max = 500, message = "审批意见不能超过500字")
    private String comment;
}
