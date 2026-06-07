package com.xiyu.bid.tendersource.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenderSourceTestRequest {

    private String platform;

    @NotBlank(message = "API endpoint is required")
    private String apiEndpoint;

    @NotBlank(message = "API key is required")
    private String apiKey;
}
