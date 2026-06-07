package com.xiyu.bid.marketinsight.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PredictionStatusUpdateRequest {
    @NotBlank
    private String status;
}
