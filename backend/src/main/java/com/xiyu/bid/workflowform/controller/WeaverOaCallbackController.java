package com.xiyu.bid.workflowform.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.workflowform.application.command.OaCallbackCommand;
import com.xiyu.bid.workflowform.application.service.WorkflowFormOaResultService;
import com.xiyu.bid.workflowform.dto.OaCallbackRequest;
import com.xiyu.bid.workflowform.infrastructure.oa.OaCallbackVerifier;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integrations/oa/weaver")
@PreAuthorize("permitAll()")
@RequiredArgsConstructor
@Slf4j
public class WeaverOaCallbackController {

    private final WorkflowFormOaResultService resultService;
    private final OaCallbackVerifier callbackVerifier;

    @PostMapping("/callback")
    public ResponseEntity<ApiResponse<Void>> callback(@Valid @RequestBody OaCallbackRequest request) {
        log.info("OA Weaver callback received: payload={}", request);
        try {
            callbackVerifier.verify(request);
            log.info("OA Weaver callback verification OK: oaInstanceId={}, status={}, operator={}",
                    request.oaInstanceId(), request.status(), request.operatorName());
            resultService.handleCallback(new OaCallbackCommand(
                    request.oaInstanceId(), request.status(), request.operatorName(), request.comment(), request.eventId()));
            log.info("OA Weaver callback handled successfully: oaInstanceId={}, status={}, eventId={}",
                    request.oaInstanceId(), request.status(), request.eventId());
            return ResponseEntity.ok(ApiResponse.success("OA 回调已处理", null));
        } catch (RuntimeException ex) {
            log.error("OA Weaver callback processing failed: payload={}", request, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("OA 回调处理失败: " + ex.getMessage()));
        }
    }
}
