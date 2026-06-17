// Input: HTTP 请求 (submit/get)
// Output: ApiResponse<RetrospectiveDTO>
// Pos: project/controller/
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.project.dto.RetrospectiveDTO;
import com.xiyu.bid.project.dto.RetrospectiveSubmitRequest;
import com.xiyu.bid.project.service.ProjectRetrospectiveService;
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
@RequestMapping("/api/projects/{projectId}/retrospective")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("isAuthenticated()")
public class ProjectRetrospectiveController {

    private final ProjectRetrospectiveService service;
    private final AuthService authService;

    /** 提交复盘：需 retrospective.submit 权限。 */
    @PostMapping
    @PreAuthorize("hasAuthority('retrospective.submit')")
    public ResponseEntity<ApiResponse<RetrospectiveDTO>> submit(
            @PathVariable Long projectId,
            @Valid @RequestBody RetrospectiveSubmitRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = currentUserId(userDetails);
        RetrospectiveDTO dto = service.submit(projectId, req, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Retrospective submitted", dto));
    }

    /** 查询复盘：ADMIN/MANAGER/STAFF 可见。 */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")
    public ResponseEntity<ApiResponse<RetrospectiveDTO>> get(@PathVariable Long projectId) {
        return service.getByProject(projectId)
                .map(dto -> ResponseEntity.ok(ApiResponse.success("ok", dto)))
                .orElseGet(() -> ResponseEntity.ok(ApiResponse.success("复盘未提交", null)));
    }

    private Long currentUserId(UserDetails userDetails) {
        if (userDetails == null || userDetails.getUsername() == null || userDetails.getUsername().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "无法识别当前用户");
        }
        return authService.resolveUserIdByUsername(userDetails.getUsername().trim());
    }
}
