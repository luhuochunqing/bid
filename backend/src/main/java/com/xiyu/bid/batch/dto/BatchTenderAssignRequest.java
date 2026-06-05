package com.xiyu.bid.batch.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class BatchTenderAssignRequest {
    @NotEmpty
    private List<@NotNull Long> tenderIds;
    @NotNull
    private Long assigneeId;
    private String remark;
}
