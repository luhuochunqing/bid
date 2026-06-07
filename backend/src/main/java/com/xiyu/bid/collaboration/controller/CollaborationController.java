// Input: collaboration service and request DTOs
// Output: Collaboration REST API endpoints
// Pos: Controller/控制器层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.collaboration.controller;

import com.xiyu.bid.collaboration.dto.CommentCreateRequest;
import com.xiyu.bid.collaboration.dto.CommentDTO;
import com.xiyu.bid.collaboration.dto.CommentUpdateRequest;
import com.xiyu.bid.collaboration.dto.ThreadCreateRequest;
import com.xiyu.bid.collaboration.dto.CollaborationThreadDTO;
import com.xiyu.bid.collaboration.dto.ThreadStatus;
import com.xiyu.bid.collaboration.service.CollaborationService;
import com.xiyu.bid.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 协作模块控制器
 * 处理协作讨论和评论相关的HTTP请求
 */
@RestController
@RequestMapping("/api/collaboration")
@RequiredArgsConstructor
@Slf4j
public class CollaborationController {

    private final CollaborationService collaborationService;

    /**
     * 根据项目ID获取讨论线程列表
     */
    @GetMapping("/threads")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<CollaborationThreadDTO>>> getThreads(
            @RequestParam(required = false) Long projectId) {

        if (projectId == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Project ID is required"));
        }

        log.info("GET /api/collaboration/threads - project: {}", projectId);
        List<CollaborationThreadDTO> threads =
                collaborationService.getThreadsByProject(projectId);
        return ResponseEntity.ok(
                ApiResponse.success("Successfully retrieved threads", threads));
    }

    /**
     * 根据ID获取讨论线程
     */
    @GetMapping("/threads/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<CollaborationThreadDTO>> getThreadById(
            @PathVariable Long id) {
        log.info("GET /api/collaboration/threads/{} - Fetching thread", id);
        CollaborationThreadDTO thread = collaborationService.getThreadById(id);
        return ResponseEntity.ok(
                ApiResponse.success("Successfully retrieved thread", thread));
    }

    /**
     * 创建讨论线程
     */
    @PostMapping("/threads")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<CollaborationThreadDTO>> createThread(
            @Valid @RequestBody ThreadCreateRequest request) {

        log.info("POST /api/collaboration/threads - project: {}",
                request.getProjectId());
        CollaborationThreadDTO createdThread =
                collaborationService.createThread(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Thread created successfully",
                        createdThread));
    }

    /**
     * 更新线程状态
     */
    @PutMapping("/threads/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<CollaborationThreadDTO>> updateThreadStatus(
            @PathVariable Long id,
            @RequestParam ThreadStatus status) {

        log.info("PUT /api/collaboration/threads/{}/status - status: {}",
                id, status);
        CollaborationThreadDTO updatedThread =
                collaborationService.updateThreadStatus(id, status);
        return ResponseEntity.ok(
                ApiResponse.success("Thread status updated successfully",
                        updatedThread));
    }

    /**
     * 添加评论
     */
    @PostMapping("/threads/{id}/comments")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<CommentDTO>> addComment(
            @PathVariable Long id,
            @Valid @RequestBody CommentCreateRequest request) {

        log.info("POST /api/collaboration/threads/{}/comments - Adding comment",
                id);
        CommentDTO createdComment =
                collaborationService.addComment(id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Comment added successfully",
                        createdComment));
    }

    /**
     * 更新评论
     */
    @PutMapping("/comments/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<CommentDTO>> updateComment(
            @PathVariable Long id,
            @Valid @RequestBody CommentUpdateRequest request) {

        log.info("PUT /api/collaboration/comments/{} - Updating comment", id);
        CommentDTO updatedComment =
                collaborationService.updateComment(id, request);
        return ResponseEntity.ok(
                ApiResponse.success("Comment updated successfully",
                        updatedComment));
    }

    /**
     * 删除评论（软删除）
     */
    @DeleteMapping("/comments/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @PathVariable Long id) {
        log.info("DELETE /api/collaboration/comments/{} - Deleting comment", id);
        collaborationService.deleteComment(id);
        return ResponseEntity.ok(
                ApiResponse.success("Comment deleted successfully", null));
    }

    /**
     * 获取提及特定用户的评论
     */
    @GetMapping("/mentions")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<CommentDTO>>> getMentions(
            @RequestParam(required = false) Long userId) {

        if (userId == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("User ID is required"));
        }

        log.info("GET /api/collaboration/mentions - user: {}", userId);
        List<CommentDTO> mentions =
                collaborationService.getMentionsForUser(userId);
        return ResponseEntity.ok(
                ApiResponse.success("Successfully retrieved mentions",
                        mentions));
    }
}
