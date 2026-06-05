package com.xiyu.bid.bidmatch.controller;

import com.xiyu.bid.bidmatch.application.BidMatchModelAppService;
import com.xiyu.bid.bidmatch.dto.BidMatchActivationResponse;
import com.xiyu.bid.bidmatch.dto.BidMatchModelRequest;
import com.xiyu.bid.bidmatch.dto.BidMatchModelResponse;
import com.xiyu.bid.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/bid-match/models")
@RequiredArgsConstructor
public class BidMatchModelController {

    private final BidMatchModelAppService modelAppService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<BidMatchModelResponse>>> listModels() {
        return ResponseEntity.ok(ApiResponse.success(modelAppService.listModels()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<BidMatchModelResponse>> createModel(
            @RequestBody BidMatchModelRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("投标匹配评分模型已创建", modelAppService.createModel(request)));
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<BidMatchModelResponse>> updateModel(
            @RequestBody BidMatchModelRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("投标匹配评分模型已更新", modelAppService.updateModel(request)));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<BidMatchActivationResponse>> activateModel(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("投标匹配评分模型已激活", modelAppService.activateModel(id)));
    }
}
