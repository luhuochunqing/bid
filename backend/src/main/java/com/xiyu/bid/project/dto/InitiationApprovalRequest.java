package com.xiyu.bid.project.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 立项审核通过请求。
 * 产品蓝图 V1.1 §4.3：审核通过必须分配投标负责人。
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

    private String reviewerNotes;
}
