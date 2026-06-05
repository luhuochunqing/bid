// Input: HTTP 请求 (register/get)
// Output: ApiResponse<ResultDTO>
// Pos: project/controller/
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.project.dto.ResultDTO;
import com.xiyu.bid.project.dto.ResultRegistrationRequest;
import com.xiyu.bid.project.service.ProjectResultRegistrationService;
import com.xiyu.bid.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/projects/{projectId}/result")
@RequiredArgsConstructor
@Slf4j
public class ProjectResultController {

    private final ProjectResultRegistrationService service;
    private final AuthService authService;

    /** 登记结果：投标负责人/辅助人员（bid_lead → MANAGER，task_executor → STAFF）。 */
    @PostMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<ResultDTO>> register(
            @PathVariable Long projectId,
            @Valid @RequestBody ResultRegistrationRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = currentUserId(userDetails);
        ResultDTO dto = service.register(projectId, req, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Result registered", dto));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")
    public ResponseEntity<ApiResponse<ResultDTO>> get(@PathVariable Long projectId) {
        return service.getByProject(projectId)
                .map(dto -> ResponseEntity.ok(ApiResponse.success("ok", dto)))
                .orElseGet(() -> ResponseEntity.ok(ApiResponse.success("结果未登记", null)));
    }

    private Long currentUserId(UserDetails userDetails) {
        if (userDetails == null || userDetails.getUsername() == null || userDetails.getUsername().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "无法识别当前用户");
        }
        return authService.resolveUserIdByUsername(userDetails.getUsername().trim());
    }
}
