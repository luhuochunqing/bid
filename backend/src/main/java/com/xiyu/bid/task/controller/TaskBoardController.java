package com.xiyu.bid.task.controller;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.task.dto.TaskBoardItemDTO;
import com.xiyu.bid.task.service.TaskBoardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 独立任务看板接口。
 */
@RestController
@RequestMapping("/api/task-board")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class TaskBoardController {

    private final TaskBoardService taskBoardService;

    @GetMapping("/items")
    @PreAuthorize("isAuthenticated()")
    @Auditable(action = "READ", entityType = "TaskBoard", description = "获取任务看板条目")
    public ResponseEntity<ApiResponse<List<TaskBoardItemDTO>>> getBoardItems(
            @AuthenticationPrincipal UserDetails userDetails) {
        List<TaskBoardItemDTO> items = taskBoardService.getBoardItems(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Task board items retrieved successfully", items));
    }
}
