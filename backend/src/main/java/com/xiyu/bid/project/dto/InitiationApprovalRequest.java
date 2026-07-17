package com.xiyu.bid.project.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

    /**
     * 审批模式下可编辑字段：计划入围供应商数量。
     * 产品要求：投标管理员/组长在分配投标负责人时，此字段仍可调整，随审批一起保存。
     * 可空：为 null 时不覆盖已有值。
     */
    @Min(value = 1, message = "计划入围供应商数量不能小于1")
    @Max(value = 255, message = "计划入围供应商数量不能超过255")
    private Integer expectedBidders;

    /**
     * 审批模式下可编辑字段：招标文件不利项。
     * 可空：为 null 时不覆盖已有值。
     */
    @Size(max = 500, message = "招标文件不利项不能超过500字")
    private String tenderAdverseItems;
}
