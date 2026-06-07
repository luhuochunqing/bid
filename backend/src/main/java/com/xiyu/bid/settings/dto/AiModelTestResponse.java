package com.xiyu.bid.settings.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiModelTestResponse {

    private String providerCode;
    private String status;
    private String message;
    private Instant testedAt;
}
