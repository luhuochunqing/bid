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
    private String username;
    private String password;
    private String apiKey;
    private String secretKey;
    /** 兼容旧客户端构造方式；旧百度 Key 不用于中转登录。 */
    public BaiduOcrTestRequest(String apiKey, String secretKey) {
        this.apiKey = apiKey;
        this.secretKey = secretKey;
    }
}
