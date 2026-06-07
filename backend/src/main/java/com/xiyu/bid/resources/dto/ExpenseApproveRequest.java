package com.xiyu.bid.resources.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ExpenseApproveRequest {

    @NotNull(message = "Result is required")
    private ApprovalResult result;

    private String comment;

    @NotBlank(message = "Approver is required")
    private String approver;

    public enum ApprovalResult {
        APPROVED,
        REJECTED
    }
}
