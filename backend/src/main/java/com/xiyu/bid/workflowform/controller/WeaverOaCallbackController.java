package com.xiyu.bid.workflowform.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.workflowform.application.command.OaCallbackCommand;
import com.xiyu.bid.workflowform.application.service.WorkflowFormOaResultService;
import com.xiyu.bid.workflowform.dto.OaCallbackRequest;
import com.xiyu.bid.workflowform.infrastructure.oa.OaCallbackVerifier;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integrations/oa/weaver")
@RequiredArgsConstructor
public class WeaverOaCallbackController {

    private final WorkflowFormOaResultService resultService;
    private final OaCallbackVerifier callbackVerifier;

    @PostMapping("/callback")
    public ResponseEntity<ApiResponse<Void>> callback(@Valid @RequestBody OaCallbackRequest request) {
        callbackVerifier.verify(request);
        resultService.handleCallback(new OaCallbackCommand(
                request.oaInstanceId(), request.status(), request.operatorName(), request.comment(), request.eventId()));
        return ResponseEntity.ok(ApiResponse.success("OA 回调已处理", null));
    }
}
