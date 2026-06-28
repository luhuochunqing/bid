package com.xiyu.bid.logging.dto;

import lombok.Data;

@Data
public class FrontendLogDTO {
    private String level;
    private String message;
    private String url;
    private String stack;
    private String browserInfo;
    private String route;
    private String traceId;
}
