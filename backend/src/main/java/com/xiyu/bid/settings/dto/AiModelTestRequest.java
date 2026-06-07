package com.xiyu.bid.settings.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiModelTestRequest {

    private String providerCode;
    private String baseUrl;
    private String model;
    private String apiKeyPlaintext;
}
