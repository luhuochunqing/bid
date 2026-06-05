package com.xiyu.bid.batch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class BatchTenderStatusUpdateRequest {
    @NotEmpty
    private List<@NotNull Long> tenderIds;
    @NotBlank
    private String status;
}
