package com.xiyu.bid.scoreparse.dto;

/**
 * 触发解析/打分响应（contracts/score-parse-api.md §1/§5）。
 */
public record ScoreParseTriggerDTO(String taskId, String status) {
}
