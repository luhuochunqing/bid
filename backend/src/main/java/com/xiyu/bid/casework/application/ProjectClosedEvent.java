package com.xiyu.bid.casework.application;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class ProjectClosedEvent extends ApplicationEvent {
    private final Long projectId;
    private final String projectName;

    public ProjectClosedEvent(Object source, Long projectId, String projectName) {
        super(source);
        this.projectId = projectId;
        this.projectName = projectName;
    }
}
