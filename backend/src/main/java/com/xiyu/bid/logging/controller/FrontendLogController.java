package com.xiyu.bid.logging.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.logging.dto.FrontendLogDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 前端全局异常与日志收集接口
 */
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/logs")
@PreAuthorize("permitAll()")
public class FrontendLogController {

    private static final Logger log = LoggerFactory.getLogger(FrontendLogController.class);

    @PostMapping("/report")
    public ApiResponse<Void> reportFrontendLog(@RequestBody FrontendLogDTO logDto) {
        // 如果前端传了单独的 traceId，可以记录到日志，否则依赖 MDC 已经有的
        String frontendTraceId = logDto.getTraceId() != null ? logDto.getTraceId() : "none";
        
        log.error("FRONTEND_ERROR - Level: {}, URL: {}, Route: {}, TraceId: {} \nMessage: {} \nStack: {} \nBrowser: {}",
                logDto.getLevel(),
                logDto.getUrl(),
                logDto.getRoute(),
                frontendTraceId,
                logDto.getMessage(),
                logDto.getStack(),
                logDto.getBrowserInfo());

        return ApiResponse.success(null);
    }
}
