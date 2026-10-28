package com.xiyu.bid.settings.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BaiduOcrTestRequest {
    private String apiKey;
    private String secretKey;
}
