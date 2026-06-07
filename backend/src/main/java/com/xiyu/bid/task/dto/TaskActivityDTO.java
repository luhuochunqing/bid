// Input: task comment/history row fields
// Output: unified activity item for TaskForm dynamic tab
// Pos: DTO/任务动态视图
package com.xiyu.bid.task.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record TaskActivityDTO(
        String type,
        Long id,
        Long taskId,
        Long actorUserId,
        String actorName,
        String content,
        String action,
        Map<String, Object> snapshot,
        LocalDateTime createdAt
) {
}
