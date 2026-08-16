package com.xiyu.bid.scoreparse.dto;

import java.time.LocalDateTime;

/**
 * 解析/打分任务进度 DTO（Redis 缓存与状态查询共用，spec 041 contracts §2/§6）。
 */
public record ScoreParseProgressDTO(
        String taskId,
        String status,
        int progress,
        String stage,
        String errorMessage,
        LocalDateTime startedAt,
        LocalDateTime completedAt
) {
}
