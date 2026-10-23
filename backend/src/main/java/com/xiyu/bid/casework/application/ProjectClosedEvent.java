package com.xiyu.bid.casework.application;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class ProjectClosedEvent extends ApplicationEvent {
    private final Long projectId;
    private final String projectName;
    /**
     * 触发案例沉淀的用户ID（仅手动触发路径设置）。
     * 蓝图 4.1.2：手动触发时异步完成通知应送达"任务发起人"，而不是固定的 managerId。
     * 自动结项路径为 null，仍 fallback 到 project.managerId。
     */
    private final Long triggerUserId;
    private final String taskId;

    public ProjectClosedEvent(Object source, Long projectId, String projectName) {
        this(source, projectId, projectName, null, null);
    }

    public ProjectClosedEvent(Object source, Long projectId, String projectName, Long triggerUserId) {
        this(source, projectId, projectName, triggerUserId, null);
    }

    public ProjectClosedEvent(Object source, Long projectId, String projectName, Long triggerUserId, String taskId) {
        super(source);
        this.projectId = projectId;
        this.projectName = projectName;
        this.triggerUserId = triggerUserId;
        this.taskId = taskId;
    }
}
