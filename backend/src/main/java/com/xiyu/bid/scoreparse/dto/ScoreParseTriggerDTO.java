package com.xiyu.bid.scoreparse.dto;

/** 触发解析/打分响应。outcome/hint 可空（SKIPPED/INCREMENTAL/FULL）。 */
public record ScoreParseTriggerDTO(String taskId, String status, String outcome, String hint) {

    public ScoreParseTriggerDTO(String taskId, String status) {
        this(taskId, status, null, null);
    }
}
