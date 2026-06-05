package com.xiyu.bid.demo.controller;

import com.xiyu.bid.demo.service.RuntimeModeService;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.dto.RuntimeModeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class RuntimeModeController {

    private final RuntimeModeService runtimeModeService;

    @GetMapping("/runtime-mode")
    public ResponseEntity<ApiResponse<RuntimeModeResponse>> getRuntimeMode() {
        return ResponseEntity.ok(ApiResponse.success(runtimeModeService.getCurrentMode()));
    }
}
