package com.xiyu.bid.project.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 立项审核通过请求。
 * 产品蓝图 V1.1 §4.3：审核通过必须分配投标负责人。
 * <p>审批意见字段统一为 {@code comment}，参照 docs/architecture/approval-contract.md。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitiationApprovalRequest {

    @NotNull(message = "必须分配投标主负责人")
    private Long primaryLeadUserId;

    private Long secondaryLeadUserId;

    private List<Long> auxiliaryUserIds;

    /** 审批意见（可选，通过操作允许不填）。 */
    @Size(max = 500, message = "审批意见不能超过500字")
    private String comment;
}
